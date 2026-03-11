package ai.architect.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;
import java.util.UUID;

public record ChatRequest(
        @NotBlank String query,
        UUID conversationId,
        Set<String> weatherProviders,
        Set<String> newsSources
) {
}
