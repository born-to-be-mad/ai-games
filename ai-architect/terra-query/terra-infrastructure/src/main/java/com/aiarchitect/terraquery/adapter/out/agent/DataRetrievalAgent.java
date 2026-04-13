package com.aiarchitect.terraquery.adapter.out.agent;

import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallingConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Specialized agent responsible for fetching raw disaster data via MCP tools.
 * Uses Spring AI's agentic loop — the LLM decides which tools to call and in what order.
 * Budget: max {@code guardrails.maxRetrievalToolCalls()} tool calls per query.
 */
@Slf4j
@Component
public class DataRetrievalAgent {

    static final String SYSTEM_PROMPT = """
            You are a data retrieval specialist for natural disaster research.
            Your role is to fetch raw factual data about natural disasters using the available tools.

            Guidelines:
            - Use query_disasters for structured filtering by type, country, or date range
            - Use get_disaster_statistics for aggregate numbers (totals, averages, worst events)
            - Use get_disaster_trends for year-by-year time series data
            - Use compare_disasters_across_countries for side-by-side country comparisons
            - Use get_deadliest_disasters for ranking by death toll
            - Use get_live_events only when the user explicitly asks about current or ongoing events
            - Fetch only the data needed to answer the question — don't over-fetch
            - Return a concise structured summary of all data retrieved

            Do NOT interpret or analyze the data — that is the job of the analysis agent.
            """;

    private final ChatClient retrievalClient;
    private final ToolProgressIndicator progressIndicator;

    public DataRetrievalAgent(ChatModel chatModel,
                               @Qualifier("dataRetrievalToolCallbackProvider")
                               SyncMcpToolCallbackProvider dataToolProvider,
                               AgentGuardrailsConfig guardrails,
                               ToolArgumentSanitizer sanitizer,
                               ToolProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;

        ToolCallback[] sanitizedTools = Arrays.stream(dataToolProvider.getToolCallbacks())
                .map(sanitizer::wrap)
                .toArray(ToolCallback[]::new);

        this.retrievalClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(sanitizedTools)
                .defaultToolCallingConfig(ToolCallingConfig.builder()
                        .maxIterations(guardrails.maxRetrievalToolCalls())
                        .build())
                .build();
    }

    /**
     * Fetch raw disaster data for the given query.
     * The LLM autonomously selects and chains tool calls (think→act→observe loop).
     *
     * @param query the data retrieval request (may be the original user query or
     *              a directive from the SupervisorAgent)
     * @return structured raw data summary ready for AnalysisSynthesisAgent
     */
    public String retrieve(String query) {
        log.info("[DataRetrievalAgent] Starting retrieval for query: {}", query);
        progressIndicator.agentThinking("DataRetrievalAgent", "Fetching disaster data...");

        String result = retrievalClient.prompt()
                .user(query)
                .call()
                .content();

        log.info("[DataRetrievalAgent] Retrieval complete");
        return result;
    }
}
