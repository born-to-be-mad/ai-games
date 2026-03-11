package ai.architect.orchestrator.repository;

import ai.architect.orchestrator.config.JpaTestConfig;
import ai.architect.orchestrator.domain.Conversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JpaTestConfig.class)
@Transactional
class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository repository;

    @Test
    void save_generatesUuidAndTimestamp() {
        Conversation saved = repository.save(Conversation.builder().build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void findAllByOrderByTimestampDesc_returnsBothConversations() {
        repository.save(Conversation.builder().topic("First").build());
        repository.save(Conversation.builder().topic("Second").build());

        List<Conversation> results = repository.findAllByOrderByTimestampDesc();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Conversation::getTopic)
                .containsExactlyInAnyOrder("First", "Second");
    }

    @Test
    void findByUserIdOrderByTimestampDesc_filtersCorrectly() {
        repository.save(Conversation.builder().userId("user-1").topic("C1").build());
        repository.save(Conversation.builder().userId("user-1").topic("C2").build());
        repository.save(Conversation.builder().userId("user-2").topic("C3").build());

        List<Conversation> user1Results = repository.findByUserIdOrderByTimestampDesc("user-1");

        assertThat(user1Results).hasSize(2);
        assertThat(user1Results).extracting(Conversation::getUserId).containsOnly("user-1");
    }

    @Test
    void findByUserIdOrderByTimestampDesc_unknownUser_returnsEmpty() {
        repository.save(Conversation.builder().userId("user-1").build());

        assertThat(repository.findByUserIdOrderByTimestampDesc("nobody")).isEmpty();
    }

    @Test
    void existsById_afterSave_returnsTrue() {
        Conversation saved = repository.save(Conversation.builder().build());

        assertThat(repository.existsById(saved.getId())).isTrue();
    }

    @Test
    void deleteById_removesConversation() {
        Conversation saved = repository.save(Conversation.builder().build());
        repository.flush();

        repository.deleteById(saved.getId());

        assertThat(repository.existsById(saved.getId())).isFalse();
    }
}
