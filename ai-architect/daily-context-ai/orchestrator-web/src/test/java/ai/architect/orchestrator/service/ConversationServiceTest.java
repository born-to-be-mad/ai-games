package ai.architect.orchestrator.service;

import ai.architect.orchestrator.domain.Conversation;
import ai.architect.orchestrator.domain.Message;
import ai.architect.orchestrator.domain.MessageRole;
import ai.architect.orchestrator.dto.ChatRequest;
import ai.architect.orchestrator.dto.ChatResponse;
import ai.architect.orchestrator.dto.ConversationDTO;
import ai.architect.orchestrator.repository.ConversationRepository;
import ai.architect.orchestrator.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private OrchestratorService orchestratorService;
    @Mock
    private MetricsService metricsService;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversationRepository, messageRepository, orchestratorService, metricsService);
    }

    // -------------------------------------------------------------------------
    // processChat
    // -------------------------------------------------------------------------

    @Test
    void processChat_noConversationId_createsNewConversation() {
        UUID newId = UUID.randomUUID();
        Conversation conv = Conversation.builder().build();
        conv.setId(newId);

        when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
        when(orchestratorService.process(any(), any(), any())).thenReturn("Answer");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatRequest req = new ChatRequest("Hello?", null, Set.of(), Set.of());
        ChatResponse response = service.processChat(req);

        assertThat(response.conversationId()).isEqualTo(newId);
        assertThat(response.answer()).isEqualTo("Answer");
    }

    @Test
    void processChat_withExistingConversationId_reusesConversation() {
        UUID existingId = UUID.randomUUID();
        Conversation conv = Conversation.builder().build();
        conv.setId(existingId);
        conv.setTopic("existing topic");

        when(conversationRepository.findById(existingId)).thenReturn(Optional.of(conv));
        when(orchestratorService.process(any(), any(), any())).thenReturn("Answer");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatRequest req = new ChatRequest("Follow-up?", existingId, null, null);
        ChatResponse response = service.processChat(req);

        assertThat(response.conversationId()).isEqualTo(existingId);
        // No new conversation created
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void processChat_savesUserAndAssistantMessages() {
        UUID convId = UUID.randomUUID();
        Conversation conv = Conversation.builder().build();
        conv.setId(convId);

        when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
        when(orchestratorService.process(any(), any(), any())).thenReturn("The answer");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processChat(new ChatRequest("My question", null, null, null));

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(2)).save(msgCaptor.capture());
        List<Message> saved = msgCaptor.getAllValues();

        assertThat(saved.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(saved.get(0).getContent()).isEqualTo("My question");
        assertThat(saved.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(saved.get(1).getContent()).isEqualTo("The answer");
    }

    @Test
    void processChat_setsTopicFromQueryWhenBlank() {
        UUID convId = UUID.randomUUID();
        Conversation conv = Conversation.builder().build();
        conv.setId(convId);

        when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
        when(orchestratorService.process(any(), any(), any())).thenReturn("Answer");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processChat(new ChatRequest("Short query", null, null, null));

        assertThat(conv.getTopic()).isEqualTo("Short query");
    }

    @Test
    void processChat_truncatesTopicAt50Chars() {
        UUID convId = UUID.randomUUID();
        Conversation conv = Conversation.builder().build();
        conv.setId(convId);
        String longQuery = "A".repeat(60);

        when(conversationRepository.save(any(Conversation.class))).thenReturn(conv);
        when(orchestratorService.process(any(), any(), any())).thenReturn("Answer");
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processChat(new ChatRequest(longQuery, null, null, null));

        assertThat(conv.getTopic()).hasSize(50);
    }

    // -------------------------------------------------------------------------
    // listConversations
    // -------------------------------------------------------------------------

    @Test
    void listConversations_returnsAllOrderedByTimestamp() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Conversation c1 = Conversation.builder().build();
        c1.setId(id1);
        c1.setTopic("First");
        Conversation c2 = Conversation.builder().build();
        c2.setId(id2);
        c2.setTopic("Second");

        when(conversationRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(c1, c2));

        List<ConversationDTO> result = service.listConversations();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(id1);
        assertThat(result.get(0).topic()).isEqualTo("First");
    }

    @Test
    void listConversations_empty_returnsEmptyList() {
        when(conversationRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of());

        assertThat(service.listConversations()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getConversation
    // -------------------------------------------------------------------------

    @Test
    void getConversation_found_returnsWithMessages() {
        UUID id = UUID.randomUUID();
        Conversation conv = Conversation.builder().build();
        conv.setId(id);
        conv.setTopic("Weather chat");

        Message msg = Message.builder()
                .conversationId(id)
                .role(MessageRole.USER)
                .content("What's the weather?")
                .build();
        msg.setId(UUID.randomUUID());
        msg.setTimestamp(LocalDateTime.now());

        when(conversationRepository.findById(id)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationIdOrderByTimestamp(id)).thenReturn(List.of(msg));

        ConversationDTO dto = service.getConversation(id);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.messages()).hasSize(1);
        assertThat(dto.messages().getFirst().content()).isEqualTo("What's the weather?");
    }

    @Test
    void getConversation_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    // -------------------------------------------------------------------------
    // deleteConversation
    // -------------------------------------------------------------------------

    @Test
    void deleteConversation_exists_deletesMessagesAndConversation() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.existsById(id)).thenReturn(true);

        service.deleteConversation(id);

        verify(messageRepository).deleteByConversationId(id);
        verify(conversationRepository).deleteById(id);
    }

    @Test
    void deleteConversation_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteConversation(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(conversationRepository, never()).deleteById(any());
    }
}
