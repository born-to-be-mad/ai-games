package ai.architect.orchestrator.service;

import ai.architect.orchestrator.config.AiProviderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderConfigService {

    private final AiProviderProperties properties;
    private final ChatClientFactory chatClientFactory;

    public ProviderConfigService(AiProviderProperties properties, ChatClientFactory chatClientFactory) {
        this.properties = properties;
        this.chatClientFactory = chatClientFactory;
    }

    public ChatClient getChatClient() {
        return getChatClient(properties.getActive());
    }

    public ChatClient getChatClient(String provider) {
        String resolved = provider != null ? provider.toLowerCase() : properties.getActive();
        return chatClientFactory.createChatClient(resolved);
    }

    public String getActiveProvider() {
        return properties.getActive();
    }

    public List<String> getAvailableProviders() {
        return chatClientFactory.getAvailableProviders();
    }
}
