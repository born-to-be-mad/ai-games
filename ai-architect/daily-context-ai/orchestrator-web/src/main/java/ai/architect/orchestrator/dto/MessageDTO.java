package ai.architect.orchestrator.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageDTO(
        UUID id,
        String role,
        String content,
        LocalDateTime timestamp
) {
}
