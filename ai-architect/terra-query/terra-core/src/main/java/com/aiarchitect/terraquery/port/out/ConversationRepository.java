package com.aiarchitect.terraquery.port.out;

import com.aiarchitect.terraquery.model.Conversation;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port — persistence interface for conversations.
 * Implemented by JpaConversationAdapter in terra-infrastructure.
 */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(String id);

    List<Conversation> findAll();

    void deleteById(String id);

    boolean existsById(String id);
}
