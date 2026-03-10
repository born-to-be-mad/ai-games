package ai.architect.orchestrator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Anthropic provider configuration.
 *
 * Spring AI autoconfigures AnthropicChatModel when an API key is present.
 * Configure via application.yml or environment variable:
 *
 * spring:
 *   ai:
 *     anthropic:
 *       api-key: ${ANTHROPIC_API_KEY}
 *       chat:
 *         model: claude-sonnet-4-6
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.anthropic.api-key")
public class AnthropicConfig {
    // Spring AI autoconfigures AnthropicChatModel — no manual bean creation needed.
}
