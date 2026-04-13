package com.aiarchitect.terraquery.model;

import java.util.List;
import java.util.Objects;

/**
 * The structured response produced by the multi-agent pipeline for a single user query.
 */
public record AgentResponse(
        String answer,
        List<String> toolsUsed,
        List<String> sources,
        List<String> agentChain,
        List<ToolCallRecord> toolCallRecords,
        VisualizationHint visualizationHint
) {

    public AgentResponse {
        Objects.requireNonNull(answer, "answer must not be null");
        toolsUsed = toolsUsed != null ? List.copyOf(toolsUsed) : List.of();
        sources = sources != null ? List.copyOf(sources) : List.of();
        agentChain = agentChain != null ? List.copyOf(agentChain) : List.of();
        toolCallRecords = toolCallRecords != null ? List.copyOf(toolCallRecords) : List.of();
    }

    public static AgentResponse of(String answer) {
        return new AgentResponse(answer, List.of(), List.of(), List.of(), List.of(), null);
    }

    public static AgentResponse of(String answer, List<String> toolsUsed,
                                   List<String> sources, List<String> agentChain) {
        return new AgentResponse(answer, toolsUsed, sources, agentChain, List.of(), null);
    }

    public record VisualizationHint(ChartType chartType, List<Object> data) {
        public enum ChartType { BAR, LINE, PIE, CHOROPLETH, NONE }
    }
}
