package com.aiarchitect.terraquery;

import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.model.ChatMessage;
import com.aiarchitect.terraquery.model.Conversation;
import com.aiarchitect.terraquery.port.out.AgentPort;
import com.aiarchitect.terraquery.port.out.ConversationRepository;
import com.aiarchitect.terraquery.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock AgentPort agentPort;
    @Mock ConversationRepository conversationRepository;

    ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(agentPort, conversationRepository, 10);
    }

    @Test
    void chat_newConversation_createsAndPersists() {
        var response = AgentResponse.of("42 people died in the 2010 Haiti earthquake.");
        when(agentPort.execute(anyString(), anyList())).thenReturn(response);
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentResponse result = chatService.chat("How many people died in Haiti 2010?", null);

        assertThat(result.answer()).contains("Haiti");
        var captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(2);
        assertThat(captor.getValue().messages().get(0).role()).isEqualTo(ChatMessage.MessageRole.USER);
        assertThat(captor.getValue().messages().get(1).role()).isEqualTo(ChatMessage.MessageRole.ASSISTANT);
    }

    @Test
    void chat_existingConversation_appendsMessages() {
        Conversation existing = Conversation.newConversation();
        existing.addMessage(ChatMessage.userMessage(existing.id(), "Tell me about floods."));
        existing.addMessage(ChatMessage.assistantMessage(existing.id(), "Floods are common in Bangladesh."));

        when(conversationRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(agentPort.execute(anyString(), anyList()))
                .thenReturn(AgentResponse.of("Yes, floods increased after 2000."));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatService.chat("Are they increasing?", existing.id());

        var captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(4);
    }

    @Test
    void chat_blankMessage_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> chatService.chat("  ", null));
        verifyNoInteractions(agentPort);
    }

    @Test
    void chat_nullMessage_throwsNullPointer() {
        assertThatNullPointerException()
                .isThrownBy(() -> chatService.chat(null, null));
        verifyNoInteractions(agentPort);
    }

    @Test
    void chat_longHistory_appliesSlidingWindow() {
        int windowSize = 4;
        chatService = new ChatService(agentPort, conversationRepository, windowSize);

        Conversation existing = Conversation.newConversation();
        for (int i = 0; i < 10; i++) {
            existing.addMessage(ChatMessage.userMessage(existing.id(), "Q" + i));
            existing.addMessage(ChatMessage.assistantMessage(existing.id(), "A" + i));
        }

        when(conversationRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(agentPort.execute(anyString(), anyList())).thenReturn(AgentResponse.of("answer"));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatService.chat("new question", existing.id());

        var historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(agentPort).execute(anyString(), historyCaptor.capture());
        // window = last 4 messages from history (before adding the new user message)
        assertThat(historyCaptor.getValue().size()).isLessThanOrEqualTo(windowSize);
    }

    @Test
    void chat_unknownConversationId_startsNewConversation() {
        when(conversationRepository.findById("unknown-id")).thenReturn(Optional.empty());
        when(agentPort.execute(anyString(), anyList())).thenReturn(AgentResponse.of("answer"));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentResponse result = chatService.chat("question", "unknown-id");

        assertThat(result).isNotNull();
        verify(conversationRepository).save(any());
    }
}
