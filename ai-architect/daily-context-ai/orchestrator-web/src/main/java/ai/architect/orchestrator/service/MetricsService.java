package ai.architect.orchestrator.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Counter chatRequests;
    private final Counter weatherAgentCalls;
    private final Counter newsAgentCalls;

    public MetricsService(MeterRegistry registry) {
        chatRequests = Counter.builder("chat.requests.total")
                .description("Total number of chat requests processed")
                .register(registry);
        weatherAgentCalls = Counter.builder("agent.weather.calls.total")
                .description("Total number of weather agent invocations")
                .register(registry);
        newsAgentCalls = Counter.builder("agent.news.calls.total")
                .description("Total number of news agent invocations")
                .register(registry);
    }

    public void incrementChatRequests() {
        chatRequests.increment();
    }

    public void incrementWeatherCalls() {
        weatherAgentCalls.increment();
    }

    public void incrementNewsCalls() {
        newsAgentCalls.increment();
    }
}
