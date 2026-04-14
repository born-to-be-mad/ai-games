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
 * Specialized agent responsible for interpreting retrieved data and synthesizing answers.
 * Has access only to the semantic search (RAG) tool for supplementary context lookup.
 * Budget: max {@code guardrails.maxAnalysisToolCalls()} tool calls.
 */
@Slf4j
@Component
public class AnalysisSynthesisAgent {

    static final String SYSTEM_PROMPT = """
            You are an expert analyst specializing in natural disaster research and communication.
            You receive raw disaster data collected by a retrieval agent and must synthesize it
            into a clear, accurate, well-structured answer for the user.

            Guidelines:
            - Use search_disasters_semantic to retrieve additional context via semantic search
              when the provided data needs supplementation or clarification
            - Interpret trends, statistics, and comparative data clearly
            - Cite specific events, dates, and numbers to support your claims
            - Acknowledge data limitations (e.g., missing records, coverage gaps)
            - Structure complex answers with clear sections when appropriate
            - Do NOT invent numbers — only report what the data shows
            - Include source attribution (EOSDIS, NOAA, NASA EONET) where relevant
            - Do NOT make more than %d total tool calls in a single response
            """;

    private final ChatClient analysisClient;
    private final ToolCallback[] ragTools;
    private final ToolProgressIndicator progressIndicator;

    public AnalysisSynthesisAgent(@Qualifier("selectedChatModel") ChatModel chatModel,
                                   @Qualifier("ragToolCallbackProvider")
                                   SyncMcpToolCallbackProvider ragToolProvider,
                                   AgentGuardrailsConfig guardrails,
                                   ToolArgumentSanitizer sanitizer,
                                   ToolProgressIndicator progressIndicator) {
        this.progressIndicator = progressIndicator;

        this.ragTools = Arrays.stream(ragToolProvider.getToolCallbacks())
                .map(sanitizer::wrap)
                .toArray(ToolCallback[]::new);

        this.analysisClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT.formatted(guardrails.maxAnalysisToolCalls()))
                .build();
    }

    /**
     * Synthesize a final answer from the provided raw data.
     */
    public String synthesize(String userQuery, String rawData) {
        log.info("[AnalysisSynthesisAgent] Synthesizing answer");
        progressIndicator.agentThinking("AnalysisSynthesisAgent", "Synthesizing answer...");

        String prompt = """
                Original user question: %s

                Retrieved data:
                %s

                Please synthesize a comprehensive answer based on this data.
                """.formatted(userQuery, rawData);

        String answer = analysisClient.prompt()
                .user(prompt)
                .toolCallbacks(ragTools)
                .call()
                .content();

        log.info("[AnalysisSynthesisAgent] Synthesis complete");
        return answer;
    }
}
