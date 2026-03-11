package ai.architect.orchestrator.dto;

import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        String answer,
        long durationMs
) {
}
