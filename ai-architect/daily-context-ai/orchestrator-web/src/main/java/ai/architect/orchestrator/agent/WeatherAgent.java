package ai.architect.orchestrator.agent;

import ai.architect.orchestrator.config.AgentProperties;
import ai.architect.orchestrator.mcp.client.WeatherMcpClient;
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

/**
 * Worker agent that queries weather MCP providers and returns a natural-language answer.
 * When multiple providers are requested, each is queried in parallel by the caller
 * ({@link ai.architect.orchestrator.service.OrchestratorService}) via virtual threads.
 *
 * Results are cached per query+providers+activeProvider for 10 minutes (see CacheConfig).
 * Calls are wrapped with SB4 declarative resilience: @Retryable (3 retries, exponential backoff)
 * and @ConcurrencyLimit (max 5 concurrent LLM calls).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAgent {

    private final WeatherMcpClient weatherMcpClient;
    private final ProviderConfigService providerConfigService;
    private final AgentProperties agentProperties;
    private final MetricsService metricsService;

    /**
     * Executes a weather query against the given set of providers.
     *
     * @param query      natural-language weather question (e.g. "Weather in London?")
     * @param providers  subset of provider names ("openmeteo", "weatherapi", "openweathermap");
     *                   empty set means all connected providers
     */
    @Cacheable(value = "weather",
               key = "#query + '_' + #providers.toString() + '_' + @providerConfigService.activeProvider",
               unless = "!#result.success()")
    @Retryable(includes = {Exception.class}, maxRetries = 3, delay = 500, multiplier = 1.5, maxDelay = 5000)
    @ConcurrencyLimit(limit = 5)
    public AgentResult execute(String query, Set<String> providers) {
        metricsService.incrementWeatherCalls();

        List<ToolCallback> callbacks = providers.isEmpty()
                ? weatherMcpClient.getToolCallbacks()
                : weatherMcpClient.getToolCallbacks(providers);

        if (callbacks.isEmpty()) {
            return AgentResult.failure("weather",
                    "No weather providers connected. Ensure MCP containers are running.");
        }

        log.info("WeatherAgent executing query='{}' providers={} tools={}", query, providers, callbacks.size());

        ChatClient chatClient = providerConfigService.getChatClient();
        ToolCallback[] tools = callbacks.toArray(ToolCallback[]::new);
        String result = chatClient.prompt()
                .system(agentProperties.weather().systemPrompt())
                .user(query)
                .toolCallbacks(tools)
                .call()
                .content();

        log.info("WeatherAgent completed query='{}' responseLength={}", query, result == null ? 0 : result.length());
        return AgentResult.success("weather", result);
    }
}
