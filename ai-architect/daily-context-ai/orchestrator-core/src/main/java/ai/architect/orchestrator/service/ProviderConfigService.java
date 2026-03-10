package ai.architect.orchestrator.service;

import ai.architect.orchestrator.config.AiProviderProperties;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Getter
@Service
public class ProviderConfigService {

    private final ChatClient chatClient;

    public ProviderConfigService(AiProviderProperties properties, ChatClientFactory chatClientFactory) {
        this.chatClient = chatClientFactory.createChatClient(properties.active());
    }

}
