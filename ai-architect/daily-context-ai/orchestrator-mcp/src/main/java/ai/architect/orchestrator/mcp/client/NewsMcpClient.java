package ai.architect.orchestrator.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides access to news MCP tools from the news-aggregator server
 * (TheNewsAPI, GNews, NewsAPI).
 * Use {@link #getToolCallbacks()} to get all news tools for passing to a ChatClient.
 */
@Component
public class NewsMcpClient {

    private static final Logger log = LoggerFactory.getLogger(NewsMcpClient.class);

    private static final String SERVER_NAME = "news-aggregator";

    private final List<McpSyncClient> newsClients;

    public NewsMcpClient(List<McpSyncClient> allMcpClients) {
        this.newsClients = allMcpClients.stream()
                .filter(c -> SERVER_NAME.equals(c.getServerInfo().name()))
                .toList();
        if (newsClients.isEmpty()) {
            log.warn("News MCP client not connected — is the news-mcp container running on port 8102?");
        } else {
            log.info("News MCP client connected: {}", SERVER_NAME);
        }
    }

    /** All news tool callbacks: get_news_thenewsapi, get_news_gnews, get_news_newsapi, get_all_news. */
    public List<ToolCallback> getToolCallbacks() {
        return McpToolUtils.getToolCallbacksFromSyncClients(newsClients);
    }

    public boolean isConnected() {
        return !newsClients.isEmpty();
    }
}
