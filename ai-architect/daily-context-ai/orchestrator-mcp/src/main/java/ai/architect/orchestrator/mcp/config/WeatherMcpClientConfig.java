package ai.architect.orchestrator.mcp.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class WeatherMcpClientConfig {

    private static final Set<String> WEATHER_SERVER_NAMES = Set.of(
            "weather-openmeteo",
            "weather-weatherapi",
            "weather-openweathermap"
    );

    private final List<McpSyncClient> mcpSyncClients;

    public WeatherMcpClientConfig(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClients = mcpSyncClients;
    }

    @Bean
    public SyncMcpToolCallbackProvider weatherToolCallbackProvider() {
        List<McpSyncClient> weatherClients = mcpSyncClients.stream()
                .filter(c -> WEATHER_SERVER_NAMES.contains(c.getServerInfo().name()))
                .toList();
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(weatherClients)
                .build();
    }
}
