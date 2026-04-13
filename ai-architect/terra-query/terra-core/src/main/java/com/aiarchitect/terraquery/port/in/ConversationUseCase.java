package com.aiarchitect.terraquery.port.in;

import com.aiarchitect.terraquery.model.Conversation;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port — conversation management operations.
 */
public interface ConversationUseCase {

    List<Conversation> listConversations();

    Optional<Conversation> findById(String conversationId);

    void deleteById(String conversationId);
}
