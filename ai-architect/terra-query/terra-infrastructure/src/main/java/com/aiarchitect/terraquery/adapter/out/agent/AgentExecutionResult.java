package com.aiarchitect.terraquery.adapter.out.agent;

import com.aiarchitect.terraquery.model.ToolCallRecord;

import java.util.List;
import java.util.Objects;

/**
 * Result envelope for a single agent execution.
 * Carries final text plus execution-derived metadata.
 */
public record AgentExecutionResult(
        String content,
        List<String> toolsUsed,
        List<String> sources,
        List<ToolCallRecord> toolCallRecords
) {
    public AgentExecutionResult {
        Objects.requireNonNull(content, "content must not be null");
        toolsUsed = toolsUsed != null ? List.copyOf(toolsUsed) : List.of();
        sources = sources != null ? List.copyOf(sources) : List.of();
        toolCallRecords = toolCallRecords != null ? List.copyOf(toolCallRecords) : List.of();
    }

    public static AgentExecutionResult ofContentOnly(String content) {
        return new AgentExecutionResult(content, List.of(), List.of(), List.of());
    }
}
