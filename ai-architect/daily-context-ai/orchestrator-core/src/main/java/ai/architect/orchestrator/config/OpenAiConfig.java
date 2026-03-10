package ai.architect.orchestrator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI provider configuration.
 *
 * Spring AI autoconfigures OpenAiChatModel when an API key is present.
 * Configure via application.yml or environment variable:
 *
 * spring:
 *   ai:
 *     openai:
 *       api-key: ${OPENAI_API_KEY}
 *       chat:
 *         model: gpt-4o-mini
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.openai.api-key")
public class OpenAiConfig {
    // Spring AI auto-configures OpenAiChatModel — no manual bean creation needed.
}
