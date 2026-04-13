package com.aiarchitect.terraquery.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain value object — a single message in a conversation.
 * No Spring or framework dependencies.
 */
public record ChatMessage(
        String id,
        String conversationId,
        MessageRole role,
        String content,
        Instant createdAt
) {

    public ChatMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    public static ChatMessage userMessage(String conversationId, String content) {
        return new ChatMessage(UUID.randomUUID().toString(), conversationId,
                MessageRole.USER, content, Instant.now());
    }

    public static ChatMessage assistantMessage(String conversationId, String content) {
        return new ChatMessage(UUID.randomUUID().toString(), conversationId,
                MessageRole.ASSISTANT, content, Instant.now());
    }

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM
    }
}
