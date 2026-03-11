package ai.architect.orchestrator.service;

import ai.architect.orchestrator.agent.AgentResult;
import ai.architect.orchestrator.agent.NewsAgent;
import ai.architect.orchestrator.agent.QueryIntent;
import ai.architect.orchestrator.agent.WeatherAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock
    private ProviderConfigService providerConfigService;
    @Mock
    private WeatherAgent weatherAgent;
    @Mock
    private NewsAgent newsAgent;
    @Mock
    private AgentCoordinationService coordinationService;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private OrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new OrchestratorService(providerConfigService, weatherAgent, newsAgent, coordinationService);
        when(providerConfigService.getChatClient()).thenReturn(chatClient);
    }

    private void stubChatChain(String response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(response);
    }

    @Test
    void process_weatherOnlyIntent_callsWeatherAgentOnly() {
        // Stub intent analysis response as JSON
        stubChatChain("{\"needsWeather\":true,\"needsNews\":false,\"location\":\"London\",\"newsQuery\":\"\"}");

        AgentResult weatherResult = AgentResult.success("weather", "Sunny, 22°C in London");
        when(coordinationService.runParallel(any())).thenReturn(List.of(weatherResult));

        // Second call to chatClient.prompt() for synthesis — reset the stub
        when(callResponseSpec.content())
                .thenReturn("{\"needsWeather\":true,\"needsNews\":false,\"location\":\"London\",\"newsQuery\":\"\"}")
                .thenReturn("It is sunny and 22°C in London.");

        String result = service.process("Weather in London?", Set.of(), Set.of());

        assertThat(result).isEqualTo("It is sunny and 22°C in London.");
        verify(coordinationService).runParallel(any());
        verifyNoInteractions(newsAgent);
    }

    @Test
    void process_newsOnlyIntent_callsNewsAgentOnly() {
        stubChatChain("{\"needsWeather\":false,\"needsNews\":true,\"location\":\"\",\"newsQuery\":\"AI breakthroughs\"}");
        when(callResponseSpec.content())
                .thenReturn("{\"needsWeather\":false,\"needsNews\":true,\"location\":\"\",\"newsQuery\":\"AI breakthroughs\"}")
                .thenReturn("Latest AI news: ...");

        AgentResult newsResult = AgentResult.success("news", "AI breakthrough article");
        when(coordinationService.runParallel(any())).thenReturn(List.of(newsResult));

        String result = service.process("Latest AI news?", Set.of(), Set.of());

        assertThat(result).isEqualTo("Latest AI news: ...");
        verify(coordinationService).runParallel(any());
        verifyNoInteractions(weatherAgent);
    }

    @Test
    void process_noAgentNeeded_answersDirectly() {
        stubChatChain("{\"needsWeather\":false,\"needsNews\":false,\"location\":\"\",\"newsQuery\":\"\"}");
        when(callResponseSpec.content())
                .thenReturn("{\"needsWeather\":false,\"needsNews\":false,\"location\":\"\",\"newsQuery\":\"\"}")
                .thenReturn("42");

        String result = service.process("What is 6 × 7?", Set.of(), Set.of());

        assertThat(result).isEqualTo("42");
        verifyNoInteractions(coordinationService);
        verifyNoInteractions(weatherAgent);
        verifyNoInteractions(newsAgent);
    }

    @Test
    void process_intentParseFailure_defaultsToWeatherAndNews() {
        // Return invalid JSON so BeanOutputConverter fails
        stubChatChain("NOT_VALID_JSON");
        when(callResponseSpec.content())
                .thenReturn("NOT_VALID_JSON")
                .thenReturn("Synthesized answer");

        AgentResult weatherResult = AgentResult.success("weather", "Weather info");
        AgentResult newsResult = AgentResult.success("news", "News info");
        when(coordinationService.runParallel(any())).thenReturn(List.of(weatherResult, newsResult));

        String result = service.process("Tell me everything", Set.of(), Set.of());

        assertThat(result).isEqualTo("Synthesized answer");
        verify(coordinationService).runParallel(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void process_weatherAndNewsIntent_dispatchesBothAgents() {
        stubChatChain("{\"needsWeather\":true,\"needsNews\":true,\"location\":\"Paris\",\"newsQuery\":\"France politics\"}");
        when(callResponseSpec.content())
                .thenReturn("{\"needsWeather\":true,\"needsNews\":true,\"location\":\"Paris\",\"newsQuery\":\"France politics\"}")
                .thenReturn("Combined answer");

        AgentResult weatherResult = AgentResult.success("weather", "Paris weather");
        AgentResult newsResult = AgentResult.success("news", "French politics news");
        when(coordinationService.runParallel(any())).thenReturn(List.of(weatherResult, newsResult));

        String result = service.process("Weather and news in France?", Set.of(), Set.of());

        assertThat(result).isEqualTo("Combined answer");

        ArgumentCaptor<List<Supplier<AgentResult>>> tasksCaptor = ArgumentCaptor.forClass(List.class);
        verify(coordinationService).runParallel(tasksCaptor.capture());
        assertThat(tasksCaptor.getValue()).hasSize(2);
    }
}
