package com.aiarchitect.terraquery.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Records a single tool call made by an agent during a conversation turn.
 * Persisted only when PersistenceScope.FULL is active.
 */
public record ToolCallRecord(
        String id,
        String messageId,
        String agentName,
        String toolName,
        String argumentsJson,
        String resultJson,
        long latencyMillis,
        Instant calledAt
) {

    public ToolCallRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(argumentsJson, "argumentsJson must not be null");
        Objects.requireNonNull(calledAt, "calledAt must not be null");
    }
}
