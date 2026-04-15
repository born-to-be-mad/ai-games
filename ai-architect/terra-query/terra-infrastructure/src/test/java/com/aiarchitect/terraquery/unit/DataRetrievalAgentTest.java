package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.adapter.out.agent.DataRetrievalAgent;
import com.aiarchitect.terraquery.adapter.out.agent.ToolArgumentSanitizer;
import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetrievalAgentTest {

    @Mock ChatModel chatModel;
    @Mock SyncMcpToolCallbackProvider dataToolProvider;
    @Mock ToolProgressIndicator progressIndicator;

    AgentGuardrailsConfig guardrails;
    DataRetrievalAgent agent;

    @BeforeEach
    void setUp() {
        guardrails = new AgentGuardrailsConfig(
                3, 8, 4, 4096, 80,
                AgentGuardrailsConfig.ContextWindowStrategy.HYBRID,
                10, 20, new BigDecimal("5.00"), 60
        );
        // Return an empty tool array so ChatClient doesn't try to call the MCP server
        when(dataToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        agent = new DataRetrievalAgent(chatModel, dataToolProvider, guardrails,
                new ToolArgumentSanitizer(), progressIndicator);
    }

    @Test
    void retrieve_returnsChatModelContent() {
        var response = mockChatResponse("Found 142 flood records in Bangladesh 1990–2021.");
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);

        var result = agent.retrieve("How many floods occurred in Bangladesh?");

        assertThat(result.content()).contains("142");
        assertThat(result.content()).contains("Bangladesh");
    }

    @Test
    void retrieve_emptyQuery_stillCallsModel() {
        var response = mockChatResponse("No data found.");
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);

        var result = agent.retrieve("What happened?");

        assertThat(result.content()).isNotBlank();
    }

    @Test
    void systemPrompt_containsBudgetInstruction() {
        // The system prompt must include the configured max tool call count
        assertThat(DataRetrievalAgent.SYSTEM_PROMPT).contains("%d");

        String formatted = DataRetrievalAgent.SYSTEM_PROMPT.formatted(guardrails.maxRetrievalToolCalls());
        assertThat(formatted).contains("8"); // default max-retrieval-tool-calls = 8
        assertThat(formatted).contains("Do NOT make more than");
    }

    @Test
    void systemPrompt_mentioneAllRetrievalTools() {
        assertThat(DataRetrievalAgent.SYSTEM_PROMPT)
                .contains("query_disasters")
                .contains("get_disaster_statistics")
                .contains("get_disaster_trends")
                .contains("compare_disasters_across_countries")
                .contains("get_deadliest_disasters")
                .contains("get_live_events");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ChatResponse mockChatResponse(String content) {
        var generation = new Generation(new AssistantMessage(content));
        return new ChatResponse(java.util.List.of(generation));
    }
}
