package com.aiarchitect.terraquery.adapter.out.agent;

import com.aiarchitect.terraquery.config.context.ContextWindowProcessor;
import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.observability.TerraQueryMetrics;
import com.aiarchitect.terraquery.port.out.AgentPort;
import com.aiarchitect.terraquery.resilience.DailyCostGuardrail;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Implements AgentPort by coordinating DataRetrievalAgent and AnalysisSynthesisAgent.
 *
 * Flow:
 *   1. Supervisor routes user query to DataRetrievalAgent (autonomous MCP tool loop)
 *   2. Raw data passed to AnalysisSynthesisAgent (RAG + reasoning loop)
 *   3. Final answer assembled and returned
 *
 * Resilience:
 *   - @CircuitBreaker opens after 50% failures in 10-call window (30s wait)
 *   - @Retry retries once on transient ConnectException / IOException
 *   - fallbackResponse() returns graceful degradation message when CB is open
 *
 * All limits configured via AgentGuardrailsConfig (application.yml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorAgentAdapter implements AgentPort {

    private final DataRetrievalAgent dataRetrievalAgent;
    private final AnalysisSynthesisAgent analysisSynthesisAgent;
    private final AgentGuardrailsConfig guardrails;
    private final ContextWindowProcessor contextWindowProcessor;
    private final ToolProgressIndicator progressIndicator;
    private final TerraQueryMetrics metrics;
    private final DailyCostGuardrail dailyCostGuardrail;

    @Override
    @CircuitBreaker(name = "mcp-supervisor", fallbackMethod = "fallbackResponse")
    @Retry(name = "mcp-supervisor")
    public AgentResponse execute(String userQuery, List<ChatMessage> history) {
        long startNs = System.nanoTime();
        log.info("[SupervisorAgent] Processing query: {}", userQuery);
        if (!dailyCostGuardrail.canProcess()) {
            return AgentResponse.of(
                    "Daily cost cap reached for the disaster analysis service. Please try again tomorrow."
            );
        }

        List<String> agentChain = new ArrayList<>();
        agentChain.add("SupervisorAgent");

        // Apply context window strategy before passing history to sub-agents
        List<ChatMessage> windowedHistory = contextWindowProcessor.process(history, guardrails.slidingWindowSize());
        metrics.recordContextWindowStrategy(guardrails.contextWindowStrategy().name());

        // Phase 1: Data retrieval
        AgentExecutionResult retrievalResult;
        try {
            retrievalResult = executeWithTimeout(
                    () -> dataRetrievalAgent.retrieve(buildRetrievalPrompt(userQuery, windowedHistory)),
                    "DataRetrievalAgent"
            );
            agentChain.add("DataRetrievalAgent");
            metrics.recordRetrievalSuccess();
        } catch (Exception e) {
            log.error("[SupervisorAgent] Data retrieval failed: {}", e.getMessage(), e);
            metrics.recordRetrievalFailure();
            metrics.recordSupervisorFailure();
            return AgentResponse.of("I encountered an error while retrieving disaster data. "
                    + "Please try again or rephrase your question.");
        }

        // Phase 2: Analysis and synthesis
        AgentExecutionResult analysisResult;
        try {
            analysisResult = executeWithTimeout(
                    () -> analysisSynthesisAgent.synthesize(userQuery, retrievalResult.content()),
                    "AnalysisSynthesisAgent"
            );
            agentChain.add("AnalysisSynthesisAgent");
            metrics.recordAnalysisSuccess();
        } catch (Exception e) {
            log.error("[SupervisorAgent] Analysis failed: {}", e.getMessage(), e);
            metrics.recordAnalysisFailure();
            // Graceful degradation: return raw data summary if synthesis fails
            analysisResult = AgentExecutionResult.ofContentOnly(
                    "I retrieved the following data but encountered an error during synthesis:\n\n"
                            + retrievalResult.content()
            );
        }

        progressIndicator.agentThinking("SupervisorAgent", "Complete");

        metrics.recordSupervisorSuccess();
        metrics.recordChatDuration(System.nanoTime() - startNs, TimeUnit.NANOSECONDS);
        log.info("[SupervisorAgent] Done. Agent chain: {}", agentChain);
        Set<String> toolsUsed = new LinkedHashSet<>();
        toolsUsed.addAll(retrievalResult.toolsUsed());
        toolsUsed.addAll(analysisResult.toolsUsed());

        Set<String> sources = new LinkedHashSet<>();
        sources.addAll(retrievalResult.sources());
        sources.addAll(analysisResult.sources());

        List<com.aiarchitect.terraquery.model.ToolCallRecord> toolCalls = new ArrayList<>();
        toolCalls.addAll(retrievalResult.toolCallRecords());
        toolCalls.addAll(analysisResult.toolCallRecords());

        AgentResponse response = new AgentResponse(
                analysisResult.content(),
                List.copyOf(toolsUsed),
                List.copyOf(sources),
                agentChain,
                toolCalls,
                null
        );
        dailyCostGuardrail.recordUsage(response.answer(), toolCalls.size());
        return response;
    }

    /**
     * Resilience4j fallback — invoked when the circuit breaker is OPEN.
     * Returns a user-friendly degradation message instead of propagating the exception.
     */
    @SuppressWarnings("unused")
    private AgentResponse fallbackResponse(String userQuery, List<ChatMessage> history, Exception cause) {
        log.warn("[SupervisorAgent] Circuit breaker open — returning degraded response. Cause: {}",
                cause.getMessage());
        metrics.recordSupervisorFailure();
        return AgentResponse.of(
                "The disaster analysis service is temporarily under high load or unavailable. "
                + "Please try again in a moment."
        );
    }

    private AgentExecutionResult executeWithTimeout(
            java.util.concurrent.Callable<AgentExecutionResult> task,
            String agentName
    ) {
        try {
            var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return future.get(guardrails.agentTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("[" + agentName + "] execution failed: " + e.getCause().getMessage(),
                    e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[" + agentName + "] interrupted");
        } catch (java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("[" + agentName + "] timed out after "
                    + guardrails.agentTimeoutSeconds() + "s");
        }
    }

    /**
     * Build a retrieval directive from the user query and recent conversation history.
     * The history provides context (e.g. "same country as the previous question").
     */
    private String buildRetrievalPrompt(String userQuery, List<ChatMessage> history) {
        if (history.isEmpty()) {
            return userQuery;
        }
        var sb = new StringBuilder();
        sb.append("Conversation context:\n");
        history.stream()
                .filter(m -> m.role() != ChatMessage.MessageRole.SYSTEM)
                .forEach(m -> sb.append("[").append(m.role()).append("]: ").append(m.content()).append("\n"));
        sb.append("\nCurrent query: ").append(userQuery);
        return sb.toString();
    }
}
