package ai.architect.orchestrator.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Daily Context AI - Agent Orchestrator Application
 *
 * An AI-powered application that answers questions about current weather and latest news
 * using agent orchestrators and MCP servers for Open-Meteo and News APIs.
 */
@SpringBootApplication(scanBasePackages = "ai.architect.orchestrator")
@ConfigurationPropertiesScan("ai.architect.orchestrator")
@EnableJpaRepositories(basePackages = "ai.architect.orchestrator")
@EntityScan(basePackages = "ai.architect.orchestrator")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
