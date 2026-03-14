package com.aiarchitect.rag.report.infrastructure.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects the active LLM provider at runtime.
 *
 * <p>Set via {@code ai.provider.active} in {@code application.yml} or the
 * {@code AI_PROVIDER} env var. Defaults to {@code openai}.
 *
 * <p>Valid values: {@code openai}, {@code anthropic}, {@code ollama}.
 */
@ConfigurationProperties(prefix = "ai.provider")
public record AiProviderProperties(String active) {

    public AiProviderProperties {
        if (active == null || active.isBlank()) {
            active = "openai";
        }
    }
}
