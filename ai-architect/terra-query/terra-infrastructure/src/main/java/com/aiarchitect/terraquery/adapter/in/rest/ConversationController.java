package com.aiarchitect.terraquery.adapter.in.rest;

import com.aiarchitect.terraquery.model.Conversation;
import com.aiarchitect.terraquery.port.in.ConversationUseCase;
import com.aiarchitect.terraquery.service.ConversationService.ConversationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationUseCase conversationUseCase;

    @GetMapping
    public ResponseEntity<List<ConversationSummary>> listConversations() {
        List<ConversationSummary> summaries = conversationUseCase.listConversations()
                .stream()
                .map(ConversationSummary::from)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDetail> getConversation(@PathVariable String id) {
        return conversationUseCase.findById(id)
                .map(ConversationDetail::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        try {
            conversationUseCase.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (ConversationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // DTO records
    public record ConversationSummary(String id, Instant createdAt, int messageCount) {
        static ConversationSummary from(Conversation c) {
            return new ConversationSummary(c.id(), c.createdAt(), c.messages().size());
        }
    }

    public record ConversationDetail(
            String id,
            Instant createdAt,
            List<MessageView> messages
    ) {
        static ConversationDetail from(Conversation c) {
            return new ConversationDetail(
                    c.id(),
                    c.createdAt(),
                    c.messages().stream().map(MessageView::from).toList()
            );
        }

        record MessageView(String id, String role, String content, Instant createdAt) {
            static MessageView from(com.aiarchitect.terraquery.model.ChatMessage m) {
                return new MessageView(m.id(), m.role().name(), m.content(), m.createdAt());
            }
        }
    }
}
