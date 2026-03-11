package ai.architect.orchestrator.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationDTO(
        UUID id,
        LocalDateTime timestamp,
        String topic,
        List<MessageDTO> messages
) {
}
