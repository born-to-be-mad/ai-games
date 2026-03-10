package ai.architect.orchestrator.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Daily Context AI - Agent Orchestrator Application
 *
 * An AI-powered application that answers questions about current weather and latest news
 * using agent orchestrators and MCP servers for Open-Meteo and News APIs.
 */
@SpringBootApplication(scanBasePackages = "ai.architect.orchestrator")
@ConfigurationPropertiesScan("ai.architect.orchestrator")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
