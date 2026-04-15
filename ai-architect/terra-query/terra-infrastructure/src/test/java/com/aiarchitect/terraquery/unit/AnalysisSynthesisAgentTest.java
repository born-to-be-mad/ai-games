package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.adapter.out.agent.AnalysisSynthesisAgent;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisSynthesisAgentTest {

    @Mock ChatModel chatModel;
    @Mock SyncMcpToolCallbackProvider ragToolProvider;
    @Mock ToolProgressIndicator progressIndicator;

    AgentGuardrailsConfig guardrails;
    AnalysisSynthesisAgent agent;

    @BeforeEach
    void setUp() {
        guardrails = new AgentGuardrailsConfig(
                3, 8, 4, 4096, 80,
                AgentGuardrailsConfig.ContextWindowStrategy.HYBRID,
                10, 20, new BigDecimal("5.00"), 60
        );
        when(ragToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        agent = new AnalysisSynthesisAgent(chatModel, ragToolProvider, guardrails,
                new ToolArgumentSanitizer(), progressIndicator);
    }

    @Test
    void synthesize_combinedQueryAndData_returnsSynthesizedAnswer() {
        var answer = "Bangladesh has experienced a 40% increase in flood frequency since 1990, "
                + "with the deadliest event in 1998 killing over 3,000 people (NOAA/EOSDIS data).";
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockChatResponse(answer));

        var result = agent.synthesize("Are floods increasing in Bangladesh?",
                "142 flood records found; 1998 was the deadliest with 3,200 deaths.");

        assertThat(result.content()).contains("Bangladesh");
        assertThat(result.content()).contains("flood");
    }

    @Test
    void synthesize_promptIncludesBothQueryAndRawData() {
        var captured = new java.util.concurrent.atomic.AtomicReference<org.springframework.ai.chat.prompt.Prompt>();
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return mockChatResponse("Synthesized answer.");
        });

        agent.synthesize("What are the trends?", "Raw data: 50 events per decade.");

        org.springframework.ai.chat.prompt.Prompt prompt = captured.get();
        assertThat(prompt).isNotNull();
        String promptText = prompt.getContents();
        assertThat(promptText)
                .contains("What are the trends?")
                .contains("Raw data: 50 events per decade.");
    }

    @Test
    void systemPrompt_containsBudgetInstruction() {
        String formatted = AnalysisSynthesisAgent.SYSTEM_PROMPT.formatted(guardrails.maxAnalysisToolCalls());
        assertThat(formatted).contains("4"); // default max-analysis-tool-calls = 4
        assertThat(formatted).contains("Do NOT make more than");
    }

    @Test
    void systemPrompt_mentionsRagTool() {
        assertThat(AnalysisSynthesisAgent.SYSTEM_PROMPT)
                .contains("search_disasters_semantic");
    }

    @Test
    void systemPrompt_instructsNoDatInvention() {
        assertThat(AnalysisSynthesisAgent.SYSTEM_PROMPT)
                .contains("Do NOT invent numbers");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private ChatResponse mockChatResponse(String content) {
        var generation = new Generation(new AssistantMessage(content));
        return new ChatResponse(List.of(generation));
    }
}
