package com.aiarchitect.terraquery.integration;

import com.aiarchitect.terraquery.adapter.in.rest.ChatController;
import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.aiarchitect.terraquery.resilience.SimpleRequestRateLimiter;
import com.aiarchitect.terraquery.streaming.ChatEvent;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the SSE event sequence emitted by /api/v1/chat/stream.
 * Checks that ANSWER_CHUNK and ANSWER_COMPLETE events are emitted correctly.
 */
@ActiveProfiles("test")
@WebMvcTest(ChatController.class)
class StreamingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatUseCase chatUseCase;
    @MockitoBean ToolProgressIndicator progressIndicator;
    @MockitoBean SimpleRequestRateLimiter rateLimiter;

    @Test
    void streamChat_emitsAnswerEvents() {
        var agentResponse = AgentResponse.of(
                "Bangladesh floods have increased.",
                java.util.List.of("query_disasters", "get_disaster_trends"),
                java.util.List.of("EOSDIS"),
                java.util.List.of("SupervisorAgent", "DataRetrievalAgent", "AnalysisSynthesisAgent")
        );
        when(chatUseCase.chat(anyString(), isNull())).thenReturn(agentResponse);
        when(progressIndicator.events()).thenReturn(reactor.core.publisher.Flux.empty());
        when(rateLimiter.tryAcquire()).thenReturn(true);

        WebTestClient webTestClient = MockMvcWebTestClient.bindTo(mockMvc).build();

        webTestClient.post().uri("/api/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"message": "Are Bangladesh floods increasing?"}
                        """)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(String.class)
                .consumeWith(result -> {
                    // Events should include ANSWER_CHUNK and ANSWER_COMPLETE
                    var body = String.join("\n", result.getResponseBody() != null
                            ? result.getResponseBody() : java.util.List.of());
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("ANSWER_CHUNK")
                            .contains("ANSWER_COMPLETE");
                });
    }
}
