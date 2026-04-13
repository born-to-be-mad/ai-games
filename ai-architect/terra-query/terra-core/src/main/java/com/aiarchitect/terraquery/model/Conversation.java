package com.aiarchitect.terraquery.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate root — a conversation session between a user and the TerraQuery agent.
 */
public final class Conversation {

    private final String id;
    private final Instant createdAt;
    private final List<ChatMessage> messages;

    private Conversation(String id, Instant createdAt, List<ChatMessage> messages) {
        this.id = Objects.requireNonNull(id);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.messages = new ArrayList<>(messages);
    }

    public static Conversation newConversation() {
        return new Conversation(UUID.randomUUID().toString(), Instant.now(), List.of());
    }

    public static Conversation reconstitute(String id, Instant createdAt, List<ChatMessage> messages) {
        return new Conversation(id, createdAt, messages);
    }

    public void addMessage(ChatMessage message) {
        Objects.requireNonNull(message);
        messages.add(message);
    }

    public String id() { return id; }
    public Instant createdAt() { return createdAt; }
    public List<ChatMessage> messages() { return List.copyOf(messages); }

    public List<ChatMessage> recentMessages(int n) {
        int from = Math.max(0, messages.size() - n);
        return List.copyOf(messages.subList(from, messages.size()));
    }
}
