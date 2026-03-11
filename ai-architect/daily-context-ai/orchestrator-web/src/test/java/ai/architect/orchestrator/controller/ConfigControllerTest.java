package ai.architect.orchestrator.controller;

import ai.architect.orchestrator.service.ChatClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClientFactory chatClientFactory;

    @Test
    void getProviders_returnsAvailableProviders() throws Exception {
        when(chatClientFactory.getAvailableProviders()).thenReturn(List.of("ollama", "openai", "anthropic"));

        mockMvc.perform(get("/api/config/providers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("ollama"));
    }

    @Test
    void getProviders_noProviders_returnsEmptyList() throws Exception {
        when(chatClientFactory.getAvailableProviders()).thenReturn(List.of());

        mockMvc.perform(get("/api/config/providers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
