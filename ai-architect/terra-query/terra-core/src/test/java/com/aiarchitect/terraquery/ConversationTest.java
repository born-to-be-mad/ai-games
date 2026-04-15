package com.aiarchitect.terraquery;

import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.model.Conversation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ConversationTest {

    // ── reconstitute ─────────────────────────────────────────────────────────

    @Test
    void reconstitute_preservesAllFields() {
        String id = "conv-123";
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        Conversation original = Conversation.newConversation();
        original.addMessage(ChatMessage.userMessage(id, "hello"));

        Conversation restored = Conversation.reconstitute(id, created,
                List.of(ChatMessage.userMessage(id, "hello")));

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.createdAt()).isEqualTo(created);
        assertThat(restored.messages()).hasSize(1);
    }

    @Test
    void reconstitute_emptyMessages_ok() {
        Conversation conv = Conversation.reconstitute("id-1", Instant.now(), List.of());
        assertThat(conv.messages()).isEmpty();
    }

    // ── recentMessages ────────────────────────────────────────────────────────

    @Test
    void recentMessages_moreThanN_returnsLastN() {
        Conversation conv = Conversation.newConversation();
        for (int i = 0; i < 5; i++) {
            conv.addMessage(ChatMessage.userMessage(conv.id(), "msg" + i));
        }

        List<ChatMessage> recent = conv.recentMessages(3);

        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).content()).isEqualTo("msg2");
        assertThat(recent.get(2).content()).isEqualTo("msg4");
    }

    @Test
    void recentMessages_exactlyN_returnsAll() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "a"));
        conv.addMessage(ChatMessage.userMessage(conv.id(), "b"));

        assertThat(conv.recentMessages(2)).hasSize(2);
    }

    @Test
    void recentMessages_fewerThanN_returnsAll() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "only"));

        assertThat(conv.recentMessages(10)).hasSize(1);
    }

    @Test
    void recentMessages_zero_returnsEmpty() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "msg"));

        assertThat(conv.recentMessages(0)).isEmpty();
    }

    // ── addMessage ────────────────────────────────────────────────────────────

    @Test
    void addMessage_null_throwsNullPointer() {
        Conversation conv = Conversation.newConversation();
        assertThatNullPointerException()
                .isThrownBy(() -> conv.addMessage(null));
    }

    @Test
    void messages_returnsDefensiveCopy() {
        Conversation conv = Conversation.newConversation();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "first"));

        List<ChatMessage> snapshot = conv.messages();
        conv.addMessage(ChatMessage.userMessage(conv.id(), "second"));

        assertThat(snapshot).hasSize(1);   // defensive copy — not affected
        assertThat(conv.messages()).hasSize(2);
    }
}
