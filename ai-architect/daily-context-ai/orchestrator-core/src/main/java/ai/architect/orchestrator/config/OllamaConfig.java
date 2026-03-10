package ai.architect.orchestrator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama provider configuration.
 *
 * Spring AI auto-configures OllamaChatModel when spring-ai-starter-model-ollama is on the classpath.
 * Configure via application.yml:
 *
 * spring:
 *   ai:
 *     ollama:
 *       base-url: http://localhost:11434
 *       chat:
 *         model: llama3.2
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.ollama.base-url")
public class OllamaConfig {
    // Spring AI autoconfigures OllamaChatModel — no manual bean creation needed.
}
