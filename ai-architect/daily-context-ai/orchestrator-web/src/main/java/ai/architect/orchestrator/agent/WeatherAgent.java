package ai.architect.orchestrator.agent;

import ai.architect.orchestrator.config.AgentProperties;
import ai.architect.orchestrator.mcp.client.WeatherMcpClient;
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

/**
 * Worker agent that queries weather MCP providers and returns a natural-language answer.
 * When multiple providers are requested, each is queried in parallel by the caller
 * ({@link ai.architect.orchestrator.service.OrchestratorService}) via virtual threads.
 *
 * Results are cached per query+providers+activeProvider for 10 minutes (see CacheConfig).
 * Calls are wrapped with Resilience4j retry (3 attempts) and circuit breaker (50% threshold).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAgent {

    private final WeatherMcpClient weatherMcpClient;
    private final ProviderConfigService providerConfigService;
    private final AgentProperties agentProperties;
    private final MetricsService metricsService;
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Executes a weather query against the given set of providers.
     *
     * @param query      natural-language weather question (e.g. "Weather in London?")
     * @param providers  subset of provider names ("openmeteo", "weatherapi", "openweathermap");
     *                   empty set means all connected providers
     */
    @SuppressWarnings("varargs")
    @Cacheable(value = "weather",
               key = "#query + '_' + #providers.toString() + '_' + @providerConfigService.activeProvider",
               unless = "!#result.success()")
    public AgentResult execute(String query, Set<String> providers) {
        metricsService.incrementWeatherCalls();
        try {
            List<ToolCallback> callbacks = providers.isEmpty()
                    ? weatherMcpClient.getToolCallbacks()
                    : weatherMcpClient.getToolCallbacks(providers);

            if (callbacks.isEmpty()) {
                return AgentResult.failure("weather",
                        "No weather providers connected. Ensure MCP containers are running.");
            }

            log.debug("Weather agent executing query='{}' providers={}", query, providers);

            Retry retry = retryRegistry.retry("weather");
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("weather");
            ToolCallback[] tools = callbacks.toArray(ToolCallback[]::new);

            Supplier<String> callSupplier = () -> {
                ChatClient chatClient = providerConfigService.getChatClient();
                return chatClient.prompt()
                        .system(agentProperties.weather().systemPrompt())
                        .user(query)
                        .tools((Object[]) tools)
                        .call()
                        .content();
            };

            Supplier<String> decorated = CircuitBreaker.decorateSupplier(cb,
                    Retry.decorateSupplier(retry, callSupplier));

            String result = decorated.get();
            return AgentResult.success("weather", result);
        } catch (Exception e) {
            log.error("Weather agent failed for query='{}': {}", query, e.getMessage(), e);
            return AgentResult.failure("weather", "Service temporarily unavailable: " + e.getMessage());
        }
    }
}
