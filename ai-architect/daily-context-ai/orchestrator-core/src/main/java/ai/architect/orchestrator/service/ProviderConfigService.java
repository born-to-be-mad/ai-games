package ai.architect.orchestrator.service;

import ai.architect.orchestrator.config.AiProviderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderConfigService {

    private final AiProviderProperties properties;
    private final ChatClientFactory chatClientFactory;

    public ChatClient getChatClient() {
        return getChatClient(properties.active());
    }

    public ChatClient getChatClient(String provider) {
        String resolved = provider != null ? provider.toLowerCase() : properties.active();
        return chatClientFactory.createChatClient(resolved);
    }

    public String getActiveProvider() {
        return properties.active();
    }

    public List<String> getAvailableProviders() {
        return chatClientFactory.getAvailableProviders();
    }
}
