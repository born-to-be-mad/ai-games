package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.adapter.out.agent.AnalysisSynthesisAgent;
import com.aiarchitect.terraquery.adapter.out.agent.DataRetrievalAgent;
import com.aiarchitect.terraquery.adapter.out.agent.SupervisorAgentAdapter;
import com.aiarchitect.terraquery.config.context.ContextWindowProcessor;
import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupervisorAgentAdapterTest {

    @Mock DataRetrievalAgent dataRetrievalAgent;
    @Mock AnalysisSynthesisAgent analysisSynthesisAgent;
    @Mock ContextWindowProcessor contextWindowProcessor;
    @Mock ToolProgressIndicator progressIndicator;

    AgentGuardrailsConfig guardrails;
    SupervisorAgentAdapter supervisor;

    @BeforeEach
    void setUp() {
        guardrails = new AgentGuardrailsConfig(
                3, 8, 4, 4096, 80,
                AgentGuardrailsConfig.ContextWindowStrategy.HYBRID,
                10, 20, new BigDecimal("5.00"), 60
        );
        // By default context processor passes history through unchanged
        when(contextWindowProcessor.process(anyList(), anyInt())).thenAnswer(inv -> inv.getArgument(0));
        supervisor = new SupervisorAgentAdapter(
                dataRetrievalAgent, analysisSynthesisAgent, guardrails,
                contextWindowProcessor, progressIndicator);
    }

    @Test
    void execute_successfulPipeline_returnsAgentResponse() {
        when(dataRetrievalAgent.retrieve(anyString()))
                .thenReturn("142 flood records found in Bangladesh 1990–2021");
        when(analysisSynthesisAgent.synthesize(anyString(), anyString()))
                .thenReturn("Floods in Bangladesh have increased significantly since 1990.");

        AgentResponse result = supervisor.execute("Are floods increasing in Bangladesh?", List.of());

        assertThat(result.answer()).contains("Bangladesh");
        assertThat(result.agentChain())
                .contains("SupervisorAgent", "DataRetrievalAgent", "AnalysisSynthesisAgent");
    }

    @Test
    void execute_retrievalFails_returnsGracefulError() {
        when(dataRetrievalAgent.retrieve(anyString()))
                .thenThrow(new RuntimeException("MCP server unavailable"));

        AgentResponse result = supervisor.execute("What happened?", List.of());

        assertThat(result.answer()).contains("error");
        verify(analysisSynthesisAgent, never()).synthesize(anyString(), anyString());
    }

    @Test
    void execute_analysisFails_returnsRawDataFallback() {
        when(dataRetrievalAgent.retrieve(anyString())).thenReturn("raw data summary");
        when(analysisSynthesisAgent.synthesize(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM timeout"));

        AgentResponse result = supervisor.execute("Tell me about floods.", List.of());

        assertThat(result.answer()).contains("raw data summary");
    }

    @Test
    void execute_withHistory_includesContextInRetrievalPrompt() {
        when(dataRetrievalAgent.retrieve(anyString())).thenReturn("data");
        when(analysisSynthesisAgent.synthesize(anyString(), anyString())).thenReturn("answer");

        var history = List.of(
                com.aiarchitect.terraquery.model.ChatMessage.userMessage("c1", "Tell me about Bangladesh floods"),
                com.aiarchitect.terraquery.model.ChatMessage.assistantMessage("c1", "Bangladesh has frequent floods")
        );

        supervisor.execute("Are they getting worse?", history);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(dataRetrievalAgent).retrieve(captor.capture());
        assertThat(captor.getValue()).contains("Bangladesh floods");
    }
}
