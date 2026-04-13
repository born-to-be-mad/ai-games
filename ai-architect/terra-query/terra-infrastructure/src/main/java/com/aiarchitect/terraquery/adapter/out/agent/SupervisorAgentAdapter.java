package com.aiarchitect.terraquery.adapter.out.agent;

import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.port.out.AgentPort;
import com.aiarchitect.terraquery.streaming.ChatEvent;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Implements AgentPort by coordinating DataRetrievalAgent and AnalysisSynthesisAgent.
 *
 * Flow:
 *   1. Supervisor routes user query to DataRetrievalAgent (autonomous MCP tool loop)
 *   2. Raw data passed to AnalysisSynthesisAgent (RAG + reasoning loop)
 *   3. Final answer assembled and returned
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
    private final ToolProgressIndicator progressIndicator;

    @Override
    public AgentResponse execute(String userQuery, List<ChatMessage> history) {
        log.info("[SupervisorAgent] Processing query: {}", userQuery);

        List<String> agentChain = new ArrayList<>();
        agentChain.add("SupervisorAgent");

        // Phase 1: Data retrieval
        String rawData;
        try {
            rawData = executeWithTimeout(
                    () -> dataRetrievalAgent.retrieve(buildRetrievalPrompt(userQuery, history)),
                    "DataRetrievalAgent"
            );
            agentChain.add("DataRetrievalAgent");
        } catch (Exception e) {
            log.error("[SupervisorAgent] Data retrieval failed: {}", e.getMessage(), e);
            return AgentResponse.of("I encountered an error while retrieving disaster data. "
                    + "Please try again or rephrase your question.");
        }

        // Phase 2: Analysis and synthesis
        String answer;
        try {
            answer = executeWithTimeout(
                    () -> analysisSynthesisAgent.synthesize(userQuery, rawData),
                    "AnalysisSynthesisAgent"
            );
            agentChain.add("AnalysisSynthesisAgent");
        } catch (Exception e) {
            log.error("[SupervisorAgent] Analysis failed: {}", e.getMessage(), e);
            // Graceful degradation: return raw data summary if synthesis fails
            answer = "I retrieved the following data but encountered an error during synthesis:\n\n" + rawData;
        }

        progressIndicator.agentThinking("SupervisorAgent", "Complete");

        log.info("[SupervisorAgent] Done. Agent chain: {}", agentChain);
        return AgentResponse.of(answer, List.of(), List.of(), agentChain);
    }

    private String executeWithTimeout(java.util.concurrent.Callable<String> task, String agentName) {
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
