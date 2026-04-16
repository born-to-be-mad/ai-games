package com.aiarchitect.terraquery.adapter.out.agent;

import com.aiarchitect.terraquery.model.ToolCallRecord;
import com.aiarchitect.terraquery.streaming.ToolProgressIndicator;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps tool callbacks to enforce per-agent call limits and capture execution metadata.
 */
public class ToolExecutionTracker {

    private final String agentName;
    private final int maxToolCalls;
    private final ToolProgressIndicator progressIndicator;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final Set<String> toolsUsed = new LinkedHashSet<>();
    private final Set<String> sourcesUsed = new LinkedHashSet<>();
    private final List<ToolCallRecord> toolCallRecords = new ArrayList<>();

    public ToolExecutionTracker(String agentName, int maxToolCalls, ToolProgressIndicator progressIndicator) {
        this.agentName = agentName;
        this.maxToolCalls = maxToolCalls;
        this.progressIndicator = progressIndicator;
    }

    public ToolCallback wrap(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                int current = callCount.incrementAndGet();
                if (current > maxToolCalls) {
                    throw new IllegalStateException(
                            "[" + agentName + "] tool call budget exceeded: "
                                    + current + " > " + maxToolCalls);
                }

                String effectiveToolName = normalizeToolName(getToolDefinition().name());
                toolsUsed.add(effectiveToolName);
                sourcesUsed.addAll(resolveSources(effectiveToolName));

                progressIndicator.toolCallStarted(agentName, effectiveToolName);
                long startNs = System.nanoTime();
                String result = "";
                try {
                    result = delegate.call(toolInput);
                    return result;
                } finally {
                    long latencyMs = (System.nanoTime() - startNs) / 1_000_000L;
                    progressIndicator.toolCallCompleted(
                            agentName,
                            effectiveToolName,
                            preview(result)
                    );
                    toolCallRecords.add(new ToolCallRecord(
                            UUID.randomUUID().toString(),
                            "runtime",
                            agentName,
                            effectiveToolName,
                            toolInput != null ? toolInput : "",
                            result,
                            latencyMs,
                            Instant.now()
                    ));
                }
            }
        };
    }

    public AgentExecutionResult toExecutionResult(String content) {
        return new AgentExecutionResult(
                content,
                List.copyOf(toolsUsed),
                List.copyOf(sourcesUsed),
                List.copyOf(toolCallRecords)
        );
    }

    public static String normalizeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        int idx = toolName.lastIndexOf("__");
        if (idx >= 0 && idx < toolName.length() - 2) {
            return toolName.substring(idx + 2);
        }
        return toolName;
    }

    private static String preview(String result) {
        if (result == null || result.isBlank()) {
            return "(empty)";
        }
        String normalized = result.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private static List<String> resolveSources(String toolName) {
        return switch (toolName) {
            case "query_disasters",
                 "get_disaster_statistics",
                 "get_deadliest_disasters",
                 "get_disaster_trends",
                 "compare_disasters_across_countries",
                 "search_disasters_semantic" -> List.of("EOSDIS", "NOAA");
            case "get_live_events" -> List.of("NASA EONET");
            default -> List.of();
        };
    }
}
