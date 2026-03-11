package ai.architect.orchestrator.controller;

import ai.architect.orchestrator.dto.ConversationDTO;
import ai.architect.orchestrator.dto.MessageDTO;
import ai.architect.orchestrator.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConversationController.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void getConversations_returnsList() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        List<ConversationDTO> conversations = List.of(
                new ConversationDTO(id1, now, "Weather chat", List.of()),
                new ConversationDTO(id2, now.minusHours(1), "News chat", List.of())
        );
        when(conversationService.listConversations()).thenReturn(conversations);

        mockMvc.perform(get("/api/conversations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].topic").value("Weather chat"))
                .andExpect(jsonPath("$[1].topic").value("News chat"));
    }

    @Test
    void getConversations_empty_returnsEmptyArray() throws Exception {
        when(conversationService.listConversations()).thenReturn(List.of());

        mockMvc.perform(get("/api/conversations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getConversation_found_returnsWithMessages() throws Exception {
        UUID id = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        MessageDTO msg = new MessageDTO(msgId, "USER", "Hello?", now);
        ConversationDTO dto = new ConversationDTO(id, now, "Test", List.of(msg));

        when(conversationService.getConversation(id)).thenReturn(dto);

        mockMvc.perform(get("/api/conversations/{id}", id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.topic").value("Test"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("USER"));
    }

    @Test
    void getConversation_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(conversationService.getConversation(id))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Conversation not found: " + id));

        mockMvc.perform(get("/api/conversations/{id}", id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteConversation_exists_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(conversationService).deleteConversation(id);

        mockMvc.perform(delete("/api/conversations/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteConversation_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResponseStatusException(NOT_FOUND, "Conversation not found: " + id))
                .when(conversationService).deleteConversation(id);

        mockMvc.perform(delete("/api/conversations/{id}", id))
                .andExpect(status().isNotFound());
    }
}
