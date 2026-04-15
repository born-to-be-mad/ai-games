package com.aiarchitect.terraquery.adapter.out.agent;

import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Specialized agent responsible for fetching raw disaster data via MCP tools.
 * Uses Spring AI's agentic loop — the LLM decides which tools to call and in what order.
 * Budget: max {@code guardrails.maxRetrievalToolCalls()} tool calls, enforced by Spring AI
 * default tool-calling loop (Spring AI 2.x handles the iteration limit internally).
 */
@Slf4j
@Component
public class DataRetrievalAgent {

    public static final String SYSTEM_PROMPT = """
            You are a data retrieval specialist for natural disaster research.
            Your role is to fetch raw factual data about natural disasters using the available tools.

            Guidelines:
            - Use query_disasters for structured filtering by type, country, or date range
            - Use get_disaster_statistics for aggregate numbers (totals, averages, worst events)
            - Use get_disaster_trends for year-by-year time series data
            - Use compare_disasters_across_countries for side-by-side country comparisons
            - Use get_deadliest_disasters for ranking by death toll
            - Use get_live_events only when the user explicitly asks about current or ongoing events
            - Fetch only the data needed to answer the question — do not over-fetch
            - Return a concise structured summary of all data retrieved

            Do NOT interpret or analyze the data — that is the job of the analysis agent.
            Do NOT make more than %d total tool calls in a single response.
            """;

    private final ChatClient retrievalClient;
    private final ToolCallback[] sanitizedTools;
    private final ToolProgressIndicator progressIndicator;
    private final ToolArgumentSanitizer sanitizer;
    private final int maxToolCalls;

    public DataRetrievalAgent(@Qualifier("selectedChatModel") ChatModel chatModel,
                               @Qualifier("dataRetrievalToolCallbackProvider")
                               SyncMcpToolCallbackProvider dataToolProvider,
                               AgentGuardrailsConfig guardrails,
                               ToolArgumentSanitizer sanitizer,
                               ToolProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;
        this.maxToolCalls = guardrails.maxRetrievalToolCalls();

        this.sanitizer = sanitizer;
        this.sanitizedTools = Arrays.stream(dataToolProvider.getToolCallbacks())
                .map(this.sanitizer::wrap)
                .toArray(ToolCallback[]::new);

        this.retrievalClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT.formatted(guardrails.maxRetrievalToolCalls()))
                .build();
    }

    /**
     * Fetch raw disaster data for the given query.
     * The LLM autonomously selects and chains tool calls (think→act→observe loop).
     */
    public AgentExecutionResult retrieve(String query) {
        log.info("[DataRetrievalAgent] Starting retrieval for: {}", query);
        progressIndicator.agentThinking("DataRetrievalAgent", "Fetching disaster data...");
        ToolExecutionTracker tracker = new ToolExecutionTracker(
                "DataRetrievalAgent",
                maxToolCalls,
                progressIndicator
        );
        ToolCallback[] trackedCallbacks = Arrays.stream(sanitizedTools)
                .map(tracker::wrap)
                .toArray(ToolCallback[]::new);

        String result = retrievalClient.prompt()
                .user(query)
                .toolCallbacks(trackedCallbacks)
                .call()
                .content();

        log.info("[DataRetrievalAgent] Retrieval complete");
        return tracker.toExecutionResult(result);
    }
}
