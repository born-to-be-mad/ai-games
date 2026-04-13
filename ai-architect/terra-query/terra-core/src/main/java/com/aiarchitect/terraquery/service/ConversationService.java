package com.aiarchitect.terraquery.service;

import com.aiarchitect.terraquery.model.Conversation;
import com.aiarchitect.terraquery.port.in.ConversationUseCase;
import com.aiarchitect.terraquery.port.out.ConversationRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain service implementing ConversationUseCase.
 * Pure domain logic — no Spring dependencies.
 */
public class ConversationService implements ConversationUseCase {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = Objects.requireNonNull(conversationRepository);
    }

    @Override
    public List<Conversation> listConversations() {
        return conversationRepository.findAll();
    }

    @Override
    public Optional<Conversation> findById(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return conversationRepository.findById(conversationId);
    }

    @Override
    public void deleteById(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        conversationRepository.deleteById(conversationId);
    }

    public static class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException(String id) {
            super("Conversation not found: " + id);
        }
    }
}
