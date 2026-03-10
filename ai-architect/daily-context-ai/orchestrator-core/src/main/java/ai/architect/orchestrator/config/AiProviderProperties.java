package ai.architect.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.provider")
public record AiProviderProperties(String active) {

    public AiProviderProperties {
        if (active == null || active.isBlank()) {
            active = "ollama";
        }
    }
}
