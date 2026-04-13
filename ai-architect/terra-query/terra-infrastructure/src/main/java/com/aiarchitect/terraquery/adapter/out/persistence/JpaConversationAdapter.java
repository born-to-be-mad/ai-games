package com.aiarchitect.terraquery.adapter.out.persistence;

import com.aiarchitect.terraquery.config.PersistenceScopeConfig;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.model.Conversation;
import com.aiarchitect.terraquery.port.out.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of ConversationRepository.
 * Persistence scope is configurable: MESSAGES_ONLY or FULL (includes tool call args + results).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaConversationAdapter implements ConversationRepository {

    private final ConversationJpaRepository jpaRepository;
    private final PersistenceScopeConfig scopeConfig;

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        ConversationEntity entity = jpaRepository.findById(conversation.id())
                .orElseGet(() -> ConversationEntity.builder()
                        .id(conversation.id())
                        .createdAt(conversation.createdAt())
                        .build());

        entity.getMessages().clear();
        for (ChatMessage msg : conversation.messages()) {
            entity.addMessage(toEntity(msg, entity));
        }

        ConversationEntity saved = jpaRepository.save(entity);
        log.debug("Saved conversation {} with {} messages (scope={})",
                saved.getId(), saved.getMessages().size(), scopeConfig.persistenceScope());
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return jpaRepository.existsById(id);
    }

    private Conversation toDomain(ConversationEntity entity) {
        List<ChatMessage> messages = entity.getMessages().stream()
                .map(this::messageToDomain)
                .toList();
        return Conversation.reconstitute(entity.getId(), entity.getCreatedAt(), messages);
    }

    private ChatMessage messageToDomain(MessageEntity entity) {
        return new ChatMessage(
                entity.getId(),
                entity.getConversation().getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt()
        );
    }

    private MessageEntity toEntity(ChatMessage msg, ConversationEntity conv) {
        return MessageEntity.builder()
                .id(msg.id())
                .conversation(conv)
                .role(msg.role())
                .content(msg.content())
                .createdAt(msg.createdAt())
                .build();
    }
}
