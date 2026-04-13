package com.aiarchitect.terraquery.config;

import com.aiarchitect.terraquery.port.out.ConversationRepository;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.port.in.ConversationUseCase;
import com.aiarchitect.terraquery.port.out.AgentPort;
import com.aiarchitect.terraquery.service.ChatService;
import com.aiarchitect.terraquery.service.ConversationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires domain services with their port implementations.
 * Keeps terra-core domain services unaware of Spring — beans constructed here.
 */
@Configuration
public class AgentConfig {

    @Bean
    public ChatUseCase chatUseCase(AgentPort agentPort,
                                   ConversationRepository conversationRepository,
                                   AgentGuardrailsConfig guardrails) {
        return new ChatService(agentPort, conversationRepository, guardrails.slidingWindowSize());
    }

    @Bean
    public ConversationUseCase conversationUseCase(ConversationRepository conversationRepository) {
        return new ConversationService(conversationRepository);
    }
}
