package com.aiarchitect.terraquery.integration;

import com.aiarchitect.terraquery.config.TestMcpTransportConfig;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/**
 * Full multi-agent integration test using WireMock stubs for MCP and LLM APIs.
 * No real API calls — validates end-to-end pipeline plumbing and agent coordination.
 *
 * WireMock loads static stubs from src/test/resources/wiremock/mappings/ automatically
 * (mcp-initialize, mcp-tools-list, mcp-tools-call). LLM scenario stubs are set up
 * programmatically to model the sequential DataRetrieval → AnalysisSynthesis flow.
 *
 * Excludes Anthropic + Ollama auto-config to avoid ambiguous ChatModel bean during tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {AnthropicChatAutoConfiguration.class, OllamaChatAutoConfiguration.class})
@Import(TestMcpTransportConfig.class)
@TestPropertySource(properties = {
        "spring.ai.mcp.client.streamable-http.connections.terra-mcp.url=http://localhost:8099",
        "spring.ai.mcp.client.streamable-http.connections.terra-mcp.endpoint=/mcp",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.base-url=http://localhost:8099",
        "terra-query.ai.provider=openai",
        "terra-query.conversation.persistence-scope=MESSAGES_ONLY"
})
class MultiAgentIntegrationTest {

    /** WireMock loads static stubs (mcp-initialize, mcp-tools-list, mcp-tools-call) from classpath. */
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .port(8099)
                    .usingFilesUnderClasspath("wiremock"))
            .build();

    @Autowired
    ChatUseCase chatUseCase;

    @BeforeEach
    void stubLlmScenario() {
        // DataRetrievalAgent: first asks for MCP tool execution.
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("agent-pipeline")
                .whenScenarioStateIs("Started")
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-retrieval-1",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "",
                              "tool_calls": [{
                                "id": "call_query_disasters_1",
                                "type": "function",
                                "function": {
                                  "name": "query_disasters",
                                  "arguments": "{\\"disaster_type\\":\\"flood\\",\\"country\\":\\"Bangladesh\\",\\"year_from\\":1990,\\"year_to\\":2021,\\"limit\\":200}"
                                }
                              }]
                            },
                            "finish_reason": "tool_calls"
                          }],
                          "usage": {"prompt_tokens": 130, "completion_tokens": 40, "total_tokens": 170}
                        }
                        """))
                .willSetStateTo("retrieval-tool-called"));

        // DataRetrievalAgent: after tool observation, returns structured retrieval summary.
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("agent-pipeline")
                .whenScenarioStateIs("retrieval-tool-called")
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-retrieval-2",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "Retrieved 142 flood events in Bangladesh (1990–2021). Deadliest: 1998 with 3,200 deaths. Trend: frequency doubled from 8/year in 1990s to 14/year in 2010s. Sources: EOSDIS, NOAA."
                            },
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 240, "completion_tokens": 80, "total_tokens": 320}
                        }
                        """))
                .willSetStateTo("retrieval-done"));

        // AnalysisSynthesisAgent: synthesizes final answer
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("agent-pipeline")
                .whenScenarioStateIs("retrieval-done")
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-analysis-1",
                          "object": "chat.completion",
                          "model": "gpt-4o",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "Flood frequency in Bangladesh has increased significantly since 1990. Based on EOSDIS and NOAA data, there were 142 recorded flood events between 1990 and 2021, with frequency doubling from approximately 8 events/year in the 1990s to 14 events/year in the 2010s. The 1998 monsoon flood was the deadliest, causing 3,200 deaths and affecting 30 million people."
                            },
                            "finish_reason": "stop"
                          }],
                          "usage": {"prompt_tokens": 400, "completion_tokens": 100, "total_tokens": 500}
                        }
                        """)));
    }

    @Test
    void chat_fullPipeline_returnsAgentResponse() {
        var response = chatUseCase.chat("Are floods increasing in Bangladesh?", null);

        assertThat(response).isNotNull();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.answer()).containsIgnoringCase("Bangladesh");
        assertThat(response.toolsUsed()).contains("query_disasters");
        assertThat(response.sources()).contains("EOSDIS", "NOAA");
        assertThat(response.toolCallRecords())
                .isNotEmpty()
                .allSatisfy(call -> assertThat(call.toolName()).isEqualTo("query_disasters"));
        assertThat(response.toolCallRecords())
                .anySatisfy(call -> assertThat(call.argumentsJson())
                        .contains("\"country\":\"Bangladesh\""));
        assertThat(response.agentChain())
                .containsExactly("SupervisorAgent", "DataRetrievalAgent", "AnalysisSynthesisAgent");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/mcp"))
                .withRequestBody(matching("(?s).*\\\"method\\\"\\s*:\\s*\\\"tools/call\\\".*")));
    }

    @Test
    void chat_springContextLoads_allBeansWired() {
        // Validates that all beans (agents, config, MCP client) start up correctly
        // against WireMock-stubbed MCP server and OpenAI-compatible LLM endpoint.
        assertThat(chatUseCase).isNotNull();
    }

    @Test
    void chat_bothAgentsInvoked_agentChainContainsBothAgents() {
        var response = chatUseCase.chat("How many floods happened in Bangladesh?", null);

        assertThat(response.agentChain())
                .containsExactly("SupervisorAgent", "DataRetrievalAgent", "AnalysisSynthesisAgent");
        assertThat(response.toolsUsed()).contains("query_disasters");
    }
}
