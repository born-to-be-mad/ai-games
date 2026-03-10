package ai.architect.orchestrator.mcp.config;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class NewsMcpClientConfig {

    private static final Set<String> NEWS_SERVER_NAMES = Set.of("news-aggregator");

    private final List<McpSyncClient> mcpSyncClients;

    @Bean
    public SyncMcpToolCallbackProvider newsToolCallbackProvider() {
        List<McpSyncClient> newsClients = mcpSyncClients.stream()
                .filter(c -> NEWS_SERVER_NAMES.contains(c.getServerInfo().name()))
                .toList();
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(newsClients)
                .build();
    }
}
