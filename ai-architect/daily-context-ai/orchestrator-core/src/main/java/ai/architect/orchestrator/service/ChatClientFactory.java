package ai.architect.orchestrator.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChatClientFactory {

    private static final Map<String, String> PROVIDER_TO_CLASS = Map.of(
            "ollama", "OllamaChatModel",
            "openai", "OpenAiChatModel",
            "anthropic", "AnthropicChatModel"
    );

    private final Map<String, ChatModel> modelsByProvider;

    public ChatClientFactory(List<ChatModel> chatModels) {
        this.modelsByProvider = chatModels.stream()
                .collect(Collectors.toMap(ChatClientFactory::resolveProviderName, Function.identity()));
    }

    public ChatClient createChatClient(String provider) {
        ChatModel model = modelsByProvider.get(provider.toLowerCase());
        if (model == null) {
            throw new IllegalStateException(
                    "Provider '%s' is not configured. Available: %s".formatted(provider, modelsByProvider.keySet()));
        }
        return ChatClient.create(model);
    }

    public boolean isProviderAvailable(String provider) {
        return modelsByProvider.containsKey(provider.toLowerCase());
    }

    public List<String> getAvailableProviders() {
        return List.copyOf(modelsByProvider.keySet());
    }

    private static String resolveProviderName(ChatModel model) {
        String simpleName = model.getClass().getSimpleName();
        return PROVIDER_TO_CLASS.entrySet().stream()
                .filter(e -> e.getValue().equals(simpleName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(simpleName.toLowerCase().replace("chatmodel", ""));
    }
}
