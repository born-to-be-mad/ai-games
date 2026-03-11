package ai.architect.orchestrator.agent;

import ai.architect.orchestrator.config.AgentProperties;
import ai.architect.orchestrator.mcp.client.NewsMcpClient;
import ai.architect.orchestrator.service.MetricsService;
import ai.architect.orchestrator.service.ProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Worker agent that searches news across multiple sources and returns a synthesized summary.
 *
 * Results are cached per query+sources+activeProvider for 10 minutes (see CacheConfig).
 * Calls are wrapped with SB4 declarative resilience: @Retryable (3 retries, exponential backoff)
 * and @ConcurrencyLimit (max 5 concurrent LLM calls).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsAgent {

    private static final Set<String> ALL_SOURCES = Set.of("thenewsapi", "gnews", "newsapi");

    private final NewsMcpClient newsMcpClient;
    private final ProviderConfigService providerConfigService;
    private final AgentProperties agentProperties;
    private final MetricsService metricsService;

    /**
     * Executes a news search query.
     *
     * @param query    natural-language news question (e.g. "Latest AI breakthroughs")
     * @param sources  subset of source names ("thenewsapi", "gnews", "newsapi");
     *                 empty set means all connected sources
     */
    @Cacheable(value = "news",
               key = "#query + '_' + #sources.toString() + '_' + @providerConfigService.activeProvider",
               unless = "!#result.success()")
    @Retryable(includes = {Exception.class}, maxRetries = 3, delay = 500, multiplier = 1.5, maxDelay = 5000)
    @ConcurrencyLimit(limit = 5)
    public AgentResult execute(String query, Set<String> sources) {
        metricsService.incrementNewsCalls();

        if (!newsMcpClient.isConnected()) {
            return AgentResult.failure("news",
                    "News MCP server not connected. Ensure the news-mcp container is running on port 8102.");
        }

        List<ToolCallback> callbacks = newsMcpClient.getToolCallbacks();
        if (callbacks.isEmpty()) {
            return AgentResult.failure("news", "No news tools available.");
        }

        String sourcesParam = sources.isEmpty()
                ? "all"
                : sources.stream()
                        .filter(ALL_SOURCES::contains)
                        .collect(Collectors.joining(","));

        String userPrompt = query + "\nSources to use: " + sourcesParam;

        log.debug("News agent executing query='{}' sources={}", query, sourcesParam);

        ChatClient chatClient = providerConfigService.getChatClient();
        ToolCallback[] tools = callbacks.toArray(ToolCallback[]::new);
        String result = chatClient.prompt()
                .system(agentProperties.news().systemPrompt())
                .user(userPrompt)
                .toolCallbacks(tools)
                .call()
                .content();

        return AgentResult.success("news", result);
    }
}
