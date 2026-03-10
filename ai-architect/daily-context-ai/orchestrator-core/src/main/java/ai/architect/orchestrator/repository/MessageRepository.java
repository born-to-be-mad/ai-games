package ai.architect.orchestrator.repository;

import ai.architect.orchestrator.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByTimestamp(UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
