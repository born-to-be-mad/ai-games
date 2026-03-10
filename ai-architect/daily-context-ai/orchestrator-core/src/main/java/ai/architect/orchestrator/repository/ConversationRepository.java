package ai.architect.orchestrator.repository;

import ai.architect.orchestrator.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdOrderByTimestampDesc(String userId);

    List<Conversation> findAllByOrderByTimestampDesc();
}
