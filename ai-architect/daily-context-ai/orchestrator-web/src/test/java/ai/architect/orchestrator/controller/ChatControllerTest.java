package ai.architect.orchestrator.controller;

import ai.architect.orchestrator.dto.ChatRequest;
import ai.architect.orchestrator.dto.ChatResponse;
import ai.architect.orchestrator.service.ConversationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void postChat_validRequest_returns200WithResponse() throws Exception {
        UUID convId = UUID.randomUUID();
        ChatResponse response = new ChatResponse(convId, "Sunny in London!", 1500L);
        when(conversationService.processChat(any(ChatRequest.class))).thenReturn(response);

        ChatRequest request = new ChatRequest("Weather in London?", null, Set.of(), Set.of());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId.toString()))
                .andExpect(jsonPath("$.answer").value("Sunny in London!"))
                .andExpect(jsonPath("$.durationMs").value(1500));
    }

    @Test
    void postChat_blankQuery_returns400() throws Exception {
        ChatRequest request = new ChatRequest("", null, null, null);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postChat_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
