package com.aiarchitect.terraquery.unit;

import com.aiarchitect.terraquery.adapter.out.agent.ToolExecutionTracker;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ToolExecutionTrackerTest {

    @Test
    void wrapsTool_recordsUsage_andEmitsLifecycleEvents() {
        ToolProgressIndicator progress = mock(ToolProgressIndicator.class);
        ToolExecutionTracker tracker = new ToolExecutionTracker("DataRetrievalAgent", 3, progress);

        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("query_disasters");
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.call(anyString())).thenReturn("Found 10 records");

        ToolCallback wrapped = tracker.wrap(delegate);
        String result = wrapped.call("{\"country\":\"BGD\"}");
        var execution = tracker.toExecutionResult(result);

        assertThat(execution.content()).contains("Found 10");
        assertThat(execution.toolsUsed()).containsExactly("query_disasters");
        assertThat(execution.sources()).contains("EOSDIS", "NOAA");
        assertThat(execution.toolCallRecords()).hasSize(1);
        verify(progress).toolCallStarted("DataRetrievalAgent", "query_disasters");
        verify(progress).toolCallCompleted(eq("DataRetrievalAgent"), eq("query_disasters"), contains("Found 10"));
    }

    @Test
    void wrapsTool_enforcesConfiguredMaxCalls() {
        ToolProgressIndicator progress = mock(ToolProgressIndicator.class);
        ToolExecutionTracker tracker = new ToolExecutionTracker("AnalysisSynthesisAgent", 1, progress);

        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("search_disasters_semantic");
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.call(anyString())).thenReturn("ok");

        ToolCallback wrapped = tracker.wrap(delegate);
        wrapped.call("{\"query\":\"flood trend\"}");

        assertThatThrownBy(() -> wrapped.call("{\"query\":\"flood trend\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tool call budget exceeded");
    }

    @Test
    void normalizeToolName_stripsServerPrefix() {
        assertThat(ToolExecutionTracker.normalizeToolName("terra-mcp__query_disasters"))
                .isEqualTo("query_disasters");
        assertThat(ToolExecutionTracker.normalizeToolName("query_disasters"))
                .isEqualTo("query_disasters");
    }
}
