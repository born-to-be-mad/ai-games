package com.aiarchitect.rag.report.infrastructure.config;

import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wires the multi-provider ChatClient factory and selects the active {@link EmbeddingModel}.
 *
 * <p>All {@link ChatModel} and {@link EmbeddingModel} beans (OpenAI, Anthropic, Ollama)
 * are discovered via Spring AI auto-configuration. This config selects the one matching
 * the active provider so that injection points requiring a single bean work correctly.
 *
 * <p>Switch provider at runtime: {@code AI_PROVIDER=anthropic} env var or
 * {@code ai.provider.active=anthropic} in application.yml.
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiConfig {

    private static final Map<String, String> PROVIDER_TO_CHAT_CLASS = Map.of(
            "openai", "OpenAiChatModel",
            "anthropic", "AnthropicChatModel",
            "ollama", "OllamaChatModel"
    );

    private static final Map<String, String> PROVIDER_TO_EMBEDDING_CLASS = Map.of(
            "openai", "OpenAiEmbeddingModel",
            "ollama", "OllamaEmbeddingModel"
    );

    @Bean
    public Map<String, ChatClient> chatClientsByProvider(List<ChatModel> chatModels) {
        return chatModels.stream()
                .collect(Collectors.toMap(
                        AiConfig::resolveChatProvider,
                        ChatClient::create,
                        (a, b) -> a
                ));
    }

    /**
     * Selects the {@link EmbeddingModel} matching the active AI provider.
     * Marked {@code @Primary} so that all injection points (VectorStore, adapters)
     * receive the correct model even when multiple providers are on the classpath.
     */
    @Bean
    @Primary
    public EmbeddingModel activeEmbeddingModel(
            List<EmbeddingModel> embeddingModels,
            AiProviderProperties props) {

        String targetClass = PROVIDER_TO_EMBEDDING_CLASS.getOrDefault(props.active(), "");

        return embeddingModels.stream()
                .filter(m -> m.getClass().getSimpleName().equals(targetClass))
                .findFirst()
                .orElse(embeddingModels.getFirst());
    }

    private static String resolveChatProvider(ChatModel model) {
        String simpleName = model.getClass().getSimpleName();
        return PROVIDER_TO_CHAT_CLASS.entrySet().stream()
                .filter(e -> e.getValue().equals(simpleName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(simpleName.toLowerCase().replace("chatmodel", ""));
    }
}
