package com.aiarchitect.terraquery.smoke;

import com.aiarchitect.terraquery.config.TestMcpTransportConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack smoke test — validates the complete Spring Boot application
 * (all beans wired, Prometheus metrics, Resilience4j, REST endpoints)
 * against WireMock stubs for MCP and LLM APIs.
 *
 * What is validated:
 *   1. Spring context loads with all production beans (observability, resilience, agents)
 *   2. POST /api/v1/chat returns 200 with valid JSON body
 *   3. GET /actuator/health returns 200 with status=UP
 *   4. GET /actuator/prometheus returns 200 with Micrometer metrics
 *   5. Agent pipeline completes: SupervisorAgent → DataRetrievalAgent → AnalysisSynthesisAgent
 *
 * Tagged @Tag("smoke") — run with:
 *   ./gradlew :terra-infrastructure:test -PincludeTags=smoke
 */
@Tag("smoke")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {AnthropicChatAutoConfiguration.class, OllamaChatAutoConfiguration.class})
@Import(TestMcpTransportConfig.class)
@TestPropertySource(properties = {
        "spring.ai.mcp.client.streamable-http.connections.terra-mcp.url=http://localhost:8098",
        "spring.ai.mcp.client.streamable-http.connections.terra-mcp.endpoint=/mcp",
        "spring.ai.openai.api-key=smoke-test-key",
        "spring.ai.openai.base-url=http://localhost:8098",
        "terra-query.ai.provider=openai",
        "terra-query.agent.guardrails.agent-timeout-seconds=30",
        "management.tracing.enabled=false"   // no OTEL exporter in tests
})
class FullStackSmokeTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .port(8098)
                    .usingFilesUnderClasspath("wiremock"))
            .build();

    @LocalServerPort
    int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUpClient() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @BeforeEach
    void stubDependencies() {
        // DataRetrievalAgent LLM call
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("smoke-pipeline")
                .whenScenarioStateIs("Started")
                .willReturn(okJson("""
                        {
                          "id": "smoke-retrieval",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "Smoke test: retrieved 10 earthquake events in Chile (2010–2020)."
                            },
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 50, "completion_tokens": 20, "total_tokens": 70}
                        }
                        """))
                .willSetStateTo("retrieval-done"));

        // AnalysisSynthesisAgent LLM call
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("smoke-pipeline")
                .whenScenarioStateIs("retrieval-done")
                .willReturn(okJson("""
                        {
                          "id": "smoke-analysis",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "Chile experienced 10 significant earthquakes between 2010 and 2020, including the devastating 8.8 magnitude Maule earthquake in 2010."
                            },
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 200, "completion_tokens": 40, "total_tokens": 240}
                        }
                        """)));
    }

    // ── 1. Full chat pipeline ────────────────────────────────────────────────

    @Test
    void chat_fullStack_returns200WithAnswer() {
        Map<String, Object> body = webTestClient.post()
                .uri("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\": \"How many earthquakes occurred in Chile in the last decade?\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();

        assertThat(body).containsKey("answer");
        assertThat(body.get("answer").toString()).isNotBlank();
        assertThat(body).containsKey("agentChain");
    }

    @Test
    void chat_agentChain_containsAllThreeAgents() {
        Map<String, Object> body = webTestClient.post()
                .uri("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\": \"How many earthquakes occurred in Chile in the last decade?\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();

        @SuppressWarnings("unchecked")
        var chain = (List<String>) body.get("agentChain");
        assertThat(chain).containsExactly("SupervisorAgent", "DataRetrievalAgent", "AnalysisSynthesisAgent");
    }

    // ── 2. Actuator health ────────────────────────────────────────────────────

    @Test
    void actuator_health_returnsUp() {
        Map<String, Object> body = webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult().getResponseBody();

        assertThat(body.get("status")).isEqualTo("UP");
    }

    // ── 3. Prometheus metrics ─────────────────────────────────────────────────

    @Test
    void actuator_prometheus_exposesMetrics() {
        String body = webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(body)
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests_seconds");
    }

    // ── 4. Input validation ───────────────────────────────────────────────────

    @Test
    void chat_blankMessage_returns400() {
        webTestClient.post()
                .uri("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\": \"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
