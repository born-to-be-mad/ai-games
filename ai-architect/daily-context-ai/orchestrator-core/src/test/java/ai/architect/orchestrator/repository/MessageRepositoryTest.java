package ai.architect.orchestrator.repository;

import ai.architect.orchestrator.config.JpaTestConfig;
import ai.architect.orchestrator.domain.Conversation;
import ai.architect.orchestrator.domain.Message;
import ai.architect.orchestrator.domain.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JpaTestConfig.class)
@Transactional
class MessageRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private UUID conversationId;

    @BeforeEach
    void setUp() {
        conversationId = conversationRepository.save(Conversation.builder().build()).getId();
    }

    @Test
    void save_generatesUuidAndTimestamp() {
        Message saved = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content("Hello")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void findByConversationIdOrderByTimestamp_returnsMessagesForConversation() {
        messageRepository.save(Message.builder()
                .conversationId(conversationId).role(MessageRole.USER).content("Q").build());
        messageRepository.save(Message.builder()
                .conversationId(conversationId).role(MessageRole.ASSISTANT).content("A").build());

        UUID otherId = conversationRepository.save(Conversation.builder().build()).getId();
        messageRepository.save(Message.builder()
                .conversationId(otherId).role(MessageRole.USER).content("Other").build());

        List<Message> messages = messageRepository.findByConversationIdOrderByTimestamp(conversationId);

        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(Message::getConversationId).containsOnly(conversationId);
    }

    @Test
    void findByConversationIdOrderByTimestamp_unknownConversation_returnsEmpty() {
        assertThat(messageRepository.findByConversationIdOrderByTimestamp(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteByConversationId_removesOnlyTargetMessages() {
        messageRepository.save(Message.builder()
                .conversationId(conversationId).role(MessageRole.USER).content("Delete me").build());

        UUID otherId = conversationRepository.save(Conversation.builder().build()).getId();
        messageRepository.save(Message.builder()
                .conversationId(otherId).role(MessageRole.USER).content("Keep me").build());

        messageRepository.deleteByConversationId(conversationId);
        messageRepository.flush();

        assertThat(messageRepository.findByConversationIdOrderByTimestamp(conversationId)).isEmpty();
        assertThat(messageRepository.findByConversationIdOrderByTimestamp(otherId)).hasSize(1);
    }

    @Test
    void messages_preserveRoleAndContent() {
        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.SYSTEM)
                .content("You are a helpful assistant.")
                .build());

        Message msg = messageRepository.findByConversationIdOrderByTimestamp(conversationId).getFirst();

        assertThat(msg.getRole()).isEqualTo(MessageRole.SYSTEM);
        assertThat(msg.getContent()).isEqualTo("You are a helpful assistant.");
    }
}
