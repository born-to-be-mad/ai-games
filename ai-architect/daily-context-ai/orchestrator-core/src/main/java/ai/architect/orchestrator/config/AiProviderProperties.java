package ai.architect.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {

    private String active = "ollama";

    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }
}
