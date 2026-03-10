package ai.architect.orchestrator.mcp.client;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides access to weather MCP tools from Open-Meteo, WeatherAPI, and OpenWeatherMap servers.
 * Use {@link #getToolCallbacks()} to get all weather tools for passing to a ChatClient,
 * or {@link #getToolCallbacks(Set)} to select tools from specific providers.
 */
@Component
public class WeatherMcpClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherMcpClient.class);

    private static final Map<String, String> SERVER_NAME_TO_PROVIDER = Map.of(
            "weather-openmeteo", "openmeteo",
            "weather-weatherapi", "weatherapi",
            "weather-openweathermap", "openweathermap"
    );

    private final List<McpSyncClient> weatherClients;

    public WeatherMcpClient(List<McpSyncClient> allMcpClients) {
        this.weatherClients = allMcpClients.stream()
                .filter(c -> SERVER_NAME_TO_PROVIDER.containsKey(c.getServerInfo().name()))
                .toList();
        log.info("Weather MCP clients connected: {}",
                weatherClients.stream().map(c -> c.getServerInfo().name()).toList());
    }

    /** All weather tool callbacks from all connected providers. */
    public List<ToolCallback> getToolCallbacks() {
        return McpToolUtils.getToolCallbacksFromSyncClients(weatherClients);
    }

    /**
     * Tool callbacks from a specific subset of providers.
     *
     * @param providers set of provider names: "openmeteo", "weatherapi", "openweathermap"
     */
    public List<ToolCallback> getToolCallbacks(Set<String> providers) {
        List<McpSyncClient> selected = weatherClients.stream()
                .filter(c -> providers.contains(SERVER_NAME_TO_PROVIDER.get(c.getServerInfo().name())))
                .toList();
        return McpToolUtils.getToolCallbacksFromSyncClients(selected);
    }

    /** Names of currently connected weather providers. */
    public List<String> getConnectedProviders() {
        return weatherClients.stream()
                .map(c -> SERVER_NAME_TO_PROVIDER.getOrDefault(c.getServerInfo().name(), c.getServerInfo().name()))
                .collect(Collectors.toList());
    }
}
