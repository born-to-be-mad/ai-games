package ai.architect.orchestrator.agent;

import ai.architect.orchestrator.config.AgentProperties;
import ai.architect.orchestrator.mcp.client.WeatherMcpClient;
import ai.architect.orchestrator.service.ProviderConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Worker agent that queries weather MCP providers and returns a natural-language answer.
 * When multiple providers are requested, each is queried in parallel by the caller
 * ({@link ai.architect.orchestrator.service.OrchestratorService}) via virtual threads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAgent {

    private final WeatherMcpClient weatherMcpClient;
    private final ProviderConfigService providerConfigService;
    private final AgentProperties agentProperties;

    /**
     * Executes a weather query against the given set of providers.
     *
     * @param query      natural-language weather question (e.g. "Weather in London?")
     * @param providers  subset of provider names ("openmeteo", "weatherapi", "openweathermap");
     *                   empty set means all connected providers
     */
    @SuppressWarnings("varargs")
    public AgentResult execute(String query, Set<String> providers) {
        try {
            List<ToolCallback> callbacks = providers.isEmpty()
                    ? weatherMcpClient.getToolCallbacks()
                    : weatherMcpClient.getToolCallbacks(providers);

            if (callbacks.isEmpty()) {
                return AgentResult.failure("weather",
                        "No weather providers connected. Ensure MCP containers are running.");
            }

            log.debug("Weather agent executing query='{}' providers={}", query, providers);

            ChatClient chatClient = providerConfigService.getChatClient();
            ToolCallback[] tools = callbacks.toArray(ToolCallback[]::new);
            String result = chatClient.prompt()
                    .system(agentProperties.weather().systemPrompt())
                    .user(query)
                    .tools((Object[]) tools)
                    .call()
                    .content();

            return AgentResult.success("weather", result);
        } catch (Exception e) {
            log.error("Weather agent failed for query='{}': {}", query, e.getMessage(), e);
            return AgentResult.failure("weather", e.getMessage());
        }
    }
}
