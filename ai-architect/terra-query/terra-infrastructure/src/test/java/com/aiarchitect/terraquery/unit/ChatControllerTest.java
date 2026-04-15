package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.adapter.in.rest.ChatController;
import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatUseCase chatUseCase;
    @MockitoBean ToolProgressIndicator progressIndicator;

    @Test
    void postChat_validRequest_returns200WithAnswer() throws Exception {
        var response = AgentResponse.of("The 2010 Haiti earthquake killed ~220,000 people.");
        when(chatUseCase.chat(anyString(), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "What was the deadliest earthquake?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(response.answer()));
    }

    @Test
    void postChat_blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatUseCase);
    }

    @Test
    void postChat_missingMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatUseCase);
    }

    @Test
    void postChat_tooLongMessage_returns400() throws Exception {
        String tooLong = "x".repeat(2001);
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatUseCase);
    }

    @Test
    void postChat_withConversationId_passesItToUseCase() throws Exception {
        var response = AgentResponse.of("answer");
        String convId = "550e8400-e29b-41d4-a716-446655440000";
        when(chatUseCase.chat(anyString(), eq(convId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Continue.", "conversationId": "550e8400-e29b-41d4-a716-446655440000"}
                                """))
                .andExpect(status().isOk());

        verify(chatUseCase).chat("Continue.", convId);
    }
}
