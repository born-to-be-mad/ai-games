package com.aiarchitect.rag.report.infrastructure.adapter.out.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Emits custom token usage metrics for LLM calls.
 *
 * <p>Metrics:
 * <ul>
 *   <li>{@code llm.token.usage} counter with tags:
 *     {@code provider}, {@code operation}, {@code token_type=prompt|completion|total}
 *   <li>{@code llm.token.usage.missing} counter when provider does not return usage metadata
 * </ul>
 */
public final class LlmTokenMetrics {

    private LlmTokenMetrics() {
    }

    public static void record(
            MeterRegistry meterRegistry,
            String provider,
            String operation,
            ChatResponse chatResponse) {

        Usage usage = (chatResponse != null && chatResponse.getMetadata() != null)
                ? chatResponse.getMetadata().getUsage()
                : null;

        if (usage == null) {
            Counter.builder("llm.token.usage.missing")
                    .tag("provider", provider)
                    .tag("operation", operation)
                    .register(meterRegistry)
                    .increment();
            return;
        }

        incrementIfPresent(meterRegistry, provider, operation, "prompt", usage.getPromptTokens());
        incrementIfPresent(meterRegistry, provider, operation, "completion", usage.getCompletionTokens());
        incrementIfPresent(meterRegistry, provider, operation, "total", usage.getTotalTokens());
    }

    private static void incrementIfPresent(
            MeterRegistry meterRegistry,
            String provider,
            String operation,
            String tokenType,
            Integer value) {
        if (value == null || value < 0) {
            return;
        }

        Counter.builder("llm.token.usage")
                .tag("provider", provider)
                .tag("operation", operation)
                .tag("token_type", tokenType)
                .register(meterRegistry)
                .increment(value.doubleValue());
    }
}
