package com.aiarchitect.terraquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * All agent guardrail limits externalized to application.yml.
 * Binding prefix: terra-query.agent.guardrails
 */
@ConfigurationProperties(prefix = "terra-query.agent.guardrails")
public record AgentGuardrailsConfig(
        int maxSupervisorDelegations,
        int maxRetrievalToolCalls,
        int maxAnalysisToolCalls,
        int maxOutputTokens,
        int maxContextWindowUsagePercent,
        ContextWindowStrategy contextWindowStrategy,
        int slidingWindowSize,
        int maxQueriesPerMinute,
        BigDecimal dailyCostCapUsd,
        int agentTimeoutSeconds
) {

    public enum ContextWindowStrategy {
        /** Keep last N messages verbatim. */
        SLIDING_WINDOW,
        /** Compress all older messages via LLM summarization. */
        SUMMARIZING,
        /** Last N verbatim + LLM summary of older messages (default). */
        HYBRID
    }

    /**
     * Apply configured context window strategy to conversation history.
     * SUMMARIZING falls back to SLIDING_WINDOW — summarization LLM call
     * is handled by ContextWindowSummarizer in the infrastructure layer.
     */
    public List<com.aiarchitect.terraquery.model.ChatMessage> applyContextWindow(
            List<com.aiarchitect.terraquery.model.ChatMessage> history) {
        return switch (contextWindowStrategy) {
            case SLIDING_WINDOW, SUMMARIZING ->
                    history.size() <= slidingWindowSize
                            ? history
                            : history.subList(history.size() - slidingWindowSize, history.size());
            case HYBRID -> history; // Full history passed to supervisor; sub-agents receive windowed
        };
    }
}
