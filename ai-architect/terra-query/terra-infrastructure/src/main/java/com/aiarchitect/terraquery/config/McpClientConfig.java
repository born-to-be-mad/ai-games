package com.aiarchitect.terraquery.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Configures MCP client tool callback providers.
 * terra-mcp server must be reachable (see application.yml spring.ai.mcp.client config).
 */
@Configuration
public class McpClientConfig {

    /** Tool names served by terra-mcp for data retrieval (structured search + stats). */
    private static final Set<String> DATA_RETRIEVAL_TOOLS = Set.of(
            "query_disasters",
            "get_disaster_statistics",
            "get_deadliest_disasters",
            "get_disaster_trends",
            "compare_disasters_across_countries",
            "get_live_events"
    );

    /** Semantic search tool served by terra-mcp. */
    private static final Set<String> RAG_TOOLS = Set.of("search_disasters_semantic");

    @Bean
    public SyncMcpToolCallbackProvider dataRetrievalToolCallbackProvider(
            List<McpSyncClient> mcpSyncClients) {
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients)
                .toolFilter(tool -> DATA_RETRIEVAL_TOOLS.contains(tool.name()))
                .build();
    }

    @Bean
    public SyncMcpToolCallbackProvider ragToolCallbackProvider(
            List<McpSyncClient> mcpSyncClients) {
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients)
                .toolFilter(tool -> RAG_TOOLS.contains(tool.name()))
                .build();
    }
}
