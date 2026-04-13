package com.aiarchitect.terraquery.service;

import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.model.Conversation;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.port.out.AgentPort;
import com.aiarchitect.terraquery.port.out.ConversationRepository;

import java.util.List;
import java.util.Objects;

/**
 * Domain service implementing ChatUseCase.
 * Orchestrates conversation retrieval/creation → agent execution → persistence.
 * Contains zero Spring dependencies — all coupling via port interfaces.
 */
public class ChatService implements ChatUseCase {

    private final AgentPort agentPort;
    private final ConversationRepository conversationRepository;
    private final int slidingWindowSize;

    public ChatService(AgentPort agentPort,
                       ConversationRepository conversationRepository,
                       int slidingWindowSize) {
        this.agentPort = Objects.requireNonNull(agentPort);
        this.conversationRepository = Objects.requireNonNull(conversationRepository);
        this.slidingWindowSize = slidingWindowSize;
    }

    @Override
    public AgentResponse chat(String message, String conversationId) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        Conversation conversation = resolveConversation(conversationId);
        ChatMessage userMsg = ChatMessage.userMessage(conversation.id(), message);
        conversation.addMessage(userMsg);

        List<ChatMessage> windowedHistory = applyWindow(conversation.messages());

        AgentResponse response = agentPort.execute(message, windowedHistory);

        ChatMessage assistantMsg = ChatMessage.assistantMessage(conversation.id(), response.answer());
        conversation.addMessage(assistantMsg);
        conversationRepository.save(conversation);

        return response;
    }

    private Conversation resolveConversation(String conversationId) {
        if (conversationId == null) {
            return Conversation.newConversation();
        }
        return conversationRepository.findById(conversationId)
                .orElseGet(Conversation::newConversation);
    }

    private List<ChatMessage> applyWindow(List<ChatMessage> messages) {
        if (messages.size() <= slidingWindowSize) {
            return messages;
        }
        return messages.subList(messages.size() - slidingWindowSize, messages.size());
    }
}
