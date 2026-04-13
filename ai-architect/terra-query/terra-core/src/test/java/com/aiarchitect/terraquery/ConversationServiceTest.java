package com.aiarchitect.terraquery;

import com.aiarchitect.terraquery.model.Conversation;
import com.aiarchitect.terraquery.port.out.ConversationRepository;
import com.aiarchitect.terraquery.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationRepository conversationRepository;

    ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository);
    }

    @Test
    void listConversations_delegatesToRepository() {
        var conv = Conversation.newConversation();
        when(conversationRepository.findAll()).thenReturn(List.of(conv));

        assertThat(conversationService.listConversations()).hasSize(1);
        verify(conversationRepository).findAll();
    }

    @Test
    void findById_found_returnsConversation() {
        var conv = Conversation.newConversation();
        when(conversationRepository.findById(conv.id())).thenReturn(Optional.of(conv));

        var found = conversationService.findById(conv.id());

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(conv.id());
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(conversationService.findById("missing")).isEmpty();
    }

    @Test
    void deleteById_exists_callsRepository() {
        var conv = Conversation.newConversation();
        when(conversationRepository.existsById(conv.id())).thenReturn(true);

        conversationService.deleteById(conv.id());

        verify(conversationRepository).deleteById(conv.id());
    }

    @Test
    void deleteById_notFound_throwsConversationNotFoundException() {
        when(conversationRepository.existsById("ghost")).thenReturn(false);

        assertThatExceptionOfType(ConversationService.ConversationNotFoundException.class)
                .isThrownBy(() -> conversationService.deleteById("ghost"))
                .withMessageContaining("ghost");
    }

    @Test
    void findById_nullId_throwsNullPointer() {
        assertThatNullPointerException()
                .isThrownBy(() -> conversationService.findById(null));
    }

    @Test
    void deleteById_nullId_throwsNullPointer() {
        assertThatNullPointerException()
                .isThrownBy(() -> conversationService.deleteById(null));
    }
}
