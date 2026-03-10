package ai.architect.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(Weather weather, News news) {

    public record Weather(String systemPrompt) {}

    public record News(String systemPrompt) {}
}
