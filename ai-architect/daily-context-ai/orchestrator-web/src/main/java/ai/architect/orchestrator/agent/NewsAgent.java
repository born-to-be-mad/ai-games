package ai.architect.orchestrator.agent;

import ai.architect.orchestrator.config.AgentProperties;
import ai.architect.orchestrator.mcp.client.NewsMcpClient;
import ai.architect.orchestrator.service.MetricsService;
import ai.architect.orchestrator.service.ProviderConfigService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Worker agent that searches news across multiple sources and returns a synthesized summary.
 *
 * Results are cached per query+sources+activeProvider for 10 minutes (see CacheConfig).
 * Calls are wrapped with Resilience4j retry (3 attempts) and circuit breaker (50% threshold).
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
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Executes a news search query.
     *
     * @param query      natural-language news question (e.g. "Latest AI breakthroughs")
     * @param sources    subset of source names ("thenewsapi", "gnews", "newsapi");
     *                   empty set means all connected sources
     */
    @SuppressWarnings("varargs")
    @Cacheable(value = "news",
               key = "#query + '_' + #sources.toString() + '_' + @providerConfigService.activeProvider",
               unless = "!#result.success()")
    public AgentResult execute(String query, Set<String> sources) {
        metricsService.incrementNewsCalls();
        try {
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

            Retry retry = retryRegistry.retry("news");
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("news");
            ToolCallback[] tools = callbacks.toArray(ToolCallback[]::new);

            Supplier<String> callSupplier = () -> {
                ChatClient chatClient = providerConfigService.getChatClient();
                return chatClient.prompt()
                        .system(agentProperties.news().systemPrompt())
                        .user(userPrompt)
                        .tools((Object[]) tools)
                        .call()
                        .content();
            };

            Supplier<String> decorated = CircuitBreaker.decorateSupplier(cb,
                    Retry.decorateSupplier(retry, callSupplier));

            String result = decorated.get();
            return AgentResult.success("news", result);
        } catch (Exception e) {
            log.error("News agent failed for query='{}': {}", query, e.getMessage(), e);
            return AgentResult.failure("news", "Service temporarily unavailable: " + e.getMessage());
        }
    }
}
