package com.aiarchitect.terraquery.adapter.in.rest;

import com.aiarchitect.terraquery.api.ConversationsApi;
import com.aiarchitect.terraquery.api.model.Conversation;
import com.aiarchitect.terraquery.api.model.ConversationPage;
import com.aiarchitect.terraquery.api.model.Message;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.port.in.ConversationUseCase;
import com.aiarchitect.terraquery.service.ConversationService.ConversationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ConversationController implements ConversationsApi {

    private final ConversationUseCase conversationUseCase;

    @Override
    public ResponseEntity<ConversationPage> listConversations(Integer page, Integer size) {
        List<com.aiarchitect.terraquery.model.Conversation> all =
                conversationUseCase.listConversations();
        List<Conversation> content = all.stream().map(this::toApiModel).toList();
        ConversationPage result = new ConversationPage()
                .content(content)
                .totalElements(content.size())
                .totalPages(1)
                .page(0)
                .size(content.size());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<Conversation> getConversation(UUID conversationId) {
        return conversationUseCase.findById(conversationId.toString())
                .map(this::toApiModel)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> deleteConversation(UUID conversationId) {
        try {
            conversationUseCase.deleteById(conversationId.toString());
            return ResponseEntity.noContent().build();
        } catch (ConversationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Conversation toApiModel(com.aiarchitect.terraquery.model.Conversation c) {
        List<Message> messages = c.messages().stream().map(this::toApiMessage).toList();
        return new Conversation()
                .id(parseUuid(c.id()))
                .createdAt(OffsetDateTime.ofInstant(c.createdAt(), ZoneOffset.UTC))
                .messages(messages);
    }

    private Message toApiMessage(ChatMessage m) {
        return new Message()
                .id(parseUuid(m.id()))
                .role(Message.RoleEnum.fromValue(m.role().name()))
                .content(m.content())
                .createdAt(OffsetDateTime.ofInstant(m.createdAt(), ZoneOffset.UTC));
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(id.getBytes());
        }
    }
}
