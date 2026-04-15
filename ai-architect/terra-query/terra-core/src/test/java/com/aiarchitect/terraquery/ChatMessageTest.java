package com.aiarchitect.terraquery;

import com.aiarchitect.terraquery.model.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ChatMessageTest {

    @Test
    void userMessage_hasCorrectRoleAndContent() {
        ChatMessage msg = ChatMessage.userMessage("conv-1", "hello world");
        assertThat(msg.role()).isEqualTo(ChatMessage.MessageRole.USER);
        assertThat(msg.content()).isEqualTo("hello world");
        assertThat(msg.conversationId()).isEqualTo("conv-1");
        assertThat(msg.id()).isNotBlank();
        assertThat(msg.createdAt()).isNotNull();
    }

    @Test
    void assistantMessage_hasCorrectRole() {
        ChatMessage msg = ChatMessage.assistantMessage("conv-2", "I can help");
        assertThat(msg.role()).isEqualTo(ChatMessage.MessageRole.ASSISTANT);
        assertThat(msg.content()).isEqualTo("I can help");
    }

    @Test
    void blankContent_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ChatMessage.userMessage("conv-1", "   "));
    }

    @Test
    void nullConversationId_throwsNullPointer() {
        assertThatNullPointerException()
                .isThrownBy(() -> ChatMessage.userMessage(null, "content"));
    }

    @Test
    void nullContent_throwsNullPointer() {
        assertThatNullPointerException()
                .isThrownBy(() -> ChatMessage.userMessage("conv-1", null));
    }
}
