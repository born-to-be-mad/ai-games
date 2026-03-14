package com.aiarchitect.rag.report.infrastructure.config;

import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wires the multi-provider ChatClient factory.
 *
 * <p>All {@link ChatModel} beans (OpenAI, Anthropic, Ollama) are discovered via
 * Spring's auto-configuration and injected as a list. The factory resolves the
 * active provider by matching the bean's simple class name against a known map.
 *
 * <p>Switch provider at runtime: {@code AI_PROVIDER=anthropic} env var or
 * {@code ai.provider.active=anthropic} in application.yml.
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiConfig {

    private static final Map<String, String> PROVIDER_TO_CLASS = Map.of(
            "openai", "OpenAiChatModel",
            "anthropic", "AnthropicChatModel",
            "ollama", "OllamaChatModel"
    );

    @Bean
    public Map<String, ChatClient> chatClientsByProvider(List<ChatModel> chatModels) {
        return chatModels.stream()
                .collect(Collectors.toMap(
                        AiConfig::resolveProvider,
                        model -> ChatClient.create(model),
                        (a, b) -> a
                ));
    }

    private static String resolveProvider(ChatModel model) {
        String simpleName = model.getClass().getSimpleName();
        return PROVIDER_TO_CLASS.entrySet().stream()
                .filter(e -> e.getValue().equals(simpleName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(simpleName.toLowerCase().replace("chatmodel", ""));
    }
}
