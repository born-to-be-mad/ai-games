package com.aiarchitect.terraquery.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Configures MCP tool callback providers split by responsibility.
 * All tools come from a single terra-mcp server; we split by tool name
 * to give each agent access only to its relevant tools.
 */
@Configuration
@Slf4j
public class McpClientConfig {

    /** Tool names for DataRetrievalAgent — structured search and statistics. */
    private static final Set<String> DATA_RETRIEVAL_TOOL_NAMES = Set.of(
            "query_disasters",
            "get_disaster_statistics",
            "get_deadliest_disasters",
            "get_disaster_trends",
            "compare_disasters_across_countries",
            "get_live_events"
    );

    /** Tool name for AnalysisSynthesisAgent — semantic RAG search. */
    private static final Set<String> RAG_TOOL_NAMES = Set.of("search_disasters_semantic");

    /**
     * All tool callbacks from the terra-mcp server filtered to data retrieval tools.
     * Injected into DataRetrievalAgent.
     */
    @Bean("dataRetrievalToolCallbackProvider")
    public SyncMcpToolCallbackProvider dataRetrievalToolCallbackProvider(
            List<McpSyncClient> mcpSyncClients) {
        log.debug("[McpClientConfig] MCP sync clients discovered: {}",
                mcpSyncClients.stream().map(c -> c.getServerInfo().name()).toList());
        List<McpSyncClient> terraMcpClients = mcpSyncClients.stream()
                .filter(c -> c.getServerInfo().name().equals("terra-mcp"))
                .toList();
        if (terraMcpClients.isEmpty()) {
            terraMcpClients = mcpSyncClients;
        }

        List<ToolCallback> allCallbacks = McpToolUtils.getToolCallbacksFromSyncClients(terraMcpClients);
        log.debug("[McpClientConfig] All MCP callbacks: {}",
                allCallbacks.stream().map(cb -> cb.getToolDefinition().name()).toList());
        ToolCallback[] dataCallbacks = allCallbacks.stream()
                .filter(cb -> matchesConfiguredTool(cb.getToolDefinition().name(), DATA_RETRIEVAL_TOOL_NAMES))
                .toArray(ToolCallback[]::new);
        log.debug("[McpClientConfig] Data retrieval callbacks: {}",
                java.util.Arrays.stream(dataCallbacks).map(cb -> cb.getToolDefinition().name()).toList());

        // Wrap in a provider adapter
        return new FilteredToolCallbackProvider(dataCallbacks);
    }

    /**
     * All tool callbacks from the terra-mcp server filtered to RAG tool only.
     * Injected into AnalysisSynthesisAgent.
     */
    @Bean("ragToolCallbackProvider")
    public SyncMcpToolCallbackProvider ragToolCallbackProvider(
            List<McpSyncClient> mcpSyncClients) {
        List<McpSyncClient> terraMcpClients = mcpSyncClients.stream()
                .filter(c -> c.getServerInfo().name().equals("terra-mcp"))
                .toList();
        if (terraMcpClients.isEmpty()) {
            terraMcpClients = mcpSyncClients;
        }

        List<ToolCallback> allCallbacks = McpToolUtils.getToolCallbacksFromSyncClients(terraMcpClients);
        ToolCallback[] ragCallbacks = allCallbacks.stream()
                .filter(cb -> matchesConfiguredTool(cb.getToolDefinition().name(), RAG_TOOL_NAMES))
                .toArray(ToolCallback[]::new);
        log.debug("[McpClientConfig] RAG callbacks: {}",
                java.util.Arrays.stream(ragCallbacks).map(cb -> cb.getToolDefinition().name()).toList());

        return new FilteredToolCallbackProvider(ragCallbacks);
    }

    /**
     * Simple SyncMcpToolCallbackProvider adapter that serves a pre-filtered array of callbacks.
     */
    static class FilteredToolCallbackProvider extends SyncMcpToolCallbackProvider {

        private final ToolCallback[] callbacks;

        FilteredToolCallbackProvider(ToolCallback[] callbacks) {
            super(List.of());
            this.callbacks = callbacks;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            return callbacks;
        }
    }

    private static String normalizeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        int idx = toolName.lastIndexOf("__");
        if (idx >= 0 && idx < toolName.length() - 2) {
            return toolName.substring(idx + 2);
        }
        return toolName;
    }

    private static boolean matchesConfiguredTool(String rawName, Set<String> configured) {
        if (rawName == null || rawName.isBlank()) {
            return false;
        }
        String normalized = normalizeToolName(rawName);
        if (configured.contains(normalized)) {
            return true;
        }
        return configured.stream().anyMatch(rawName::endsWith);
    }
}
