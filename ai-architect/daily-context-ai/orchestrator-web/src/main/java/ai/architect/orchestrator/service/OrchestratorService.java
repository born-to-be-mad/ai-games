package ai.architect.orchestrator.service;

import ai.architect.orchestrator.agent.AgentResult;
import ai.architect.orchestrator.agent.NewsAgent;
import ai.architect.orchestrator.agent.QueryIntent;
import ai.architect.orchestrator.agent.WeatherAgent;
import ai.architect.orchestrator.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Orchestrator implementing the Orchestrator-Workers pattern:
 * <ol>
 *   <li>Analyzes the user's intent via LLM (structured output → {@link QueryIntent})</li>
 *   <li>Dispatches to {@link WeatherAgent} and/or {@link NewsAgent} in parallel (virtual threads)</li>
 *   <li>Synthesizes all agent results into a final coherent response via LLM</li>
 * </ol>
 * <p>
 * Queries that need neither weather nor news are answered directly by the LLM.
 */
@Slf4j
@Service
public class OrchestratorService {

    private final ProviderConfigService providerConfigService;
    private final WeatherAgent weatherAgent;
    private final NewsAgent newsAgent;
    private final AgentCoordinationService coordinationService;
    private final AgentProperties.Orchestrator agentProperties;

    public OrchestratorService(ProviderConfigService providerConfigService, WeatherAgent weatherAgent, NewsAgent newsAgent, AgentCoordinationService coordinationService, AgentProperties agentProperties) {
        this.providerConfigService = providerConfigService;
        this.weatherAgent = weatherAgent;
        this.newsAgent = newsAgent;
        this.coordinationService = coordinationService;
        this.agentProperties = agentProperties.orchestrator();
    }

    /**
     * Processes a user query end-to-end.
     *
     * @param userQuery        the user's natural-language question
     * @param weatherProviders optional subset of weather providers to use (empty = all)
     * @param newsSources      optional subset of news sources to use (empty = all)
     * @return synthesized natural-language answer
     */
    public String process(String userQuery,
                          Set<String> weatherProviders,
                          Set<String> newsSources) {

        ChatClient chatClient = providerConfigService.getChatClient();

        // Step 1 — Intent analysis
        QueryIntent intent = analyzeIntent(userQuery, chatClient);
        log.info("Intent for query='{}': needsWeather={} needsNews={} location='{}' newsQuery='{}'",
                userQuery, intent.needsWeather(), intent.needsNews(),
                intent.location(), intent.newsQuery());

        // Step 2 — If intent is unclear, answer directly
        if (!intent.needsWeather() && !intent.needsNews()) {
            log.info("No specialized agent needed — answering directly");
            return chatClient.prompt()
                    .system(agentProperties.directAnswerPrompt())
                    .user(userQuery)
                    .call()
                    .content();
        }

        // Step 3 — Build parallel agent tasks
        List<Supplier<AgentResult>> tasks = new ArrayList<>();

        if (intent.needsWeather()) {
            String weatherQuery = intent.location().isBlank()
                    ? userQuery
                    : "Current weather and forecast for: " + intent.location();
            log.info("Dispatching WeatherAgent with query='{}'", weatherQuery);
            tasks.add(() -> weatherAgent.execute(weatherQuery, weatherProviders));
        }

        if (intent.needsNews()) {
            String newsQuery = intent.newsQuery().isBlank() ? userQuery : intent.newsQuery();
            log.info("Dispatching NewsAgent with query='{}'", newsQuery);
            tasks.add(() -> newsAgent.execute(newsQuery, newsSources));
        }

        // Step 4 — Run agents in parallel on virtual threads
        List<AgentResult> results = coordinationService.runParallel(tasks);

        // Step 5 — Synthesize into a final answer
        log.info("Synthesizing answer from {} agent result(s) for query='{}'", results.size(), userQuery);
        return synthesize(userQuery, results, chatClient);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private QueryIntent analyzeIntent(String query, ChatClient chatClient) {
        BeanOutputConverter<QueryIntent> converter = new BeanOutputConverter<>(QueryIntent.class);
        String response = chatClient.prompt()
                .system(agentProperties.intentPrompt() + converter.getFormat())
                .user(query)
                .call()
                .content();
        try {
            return converter.convert(response);
        } catch (Exception e) {
            log.warn("Intent parsing failed ({}), defaulting to weather+news", e.getMessage());
            return new QueryIntent(true, true, "", query);
        }
    }

    private String synthesize(String originalQuery, List<AgentResult> results, ChatClient chatClient) {
        StringBuilder context = new StringBuilder();
        for (AgentResult result : results) {
            context.append("### ").append(result.agentName().toUpperCase()).append("\n");
            if (result.success()) {
                context.append(result.content());
            } else {
                context.append("Error: ").append(result.errorMessage());
            }
            context.append("\n\n");
        }

        String synthesisPrompt = "User question: " + originalQuery + "\n\nInformation gathered:\n" + context;
        log.info("Synthesizing final answer for query='{}' contextLength={}", originalQuery, context.length());
        return chatClient.prompt()
                .system(agentProperties.synthesisPrompt())
                .user(synthesisPrompt)
                .call()
                .content();
    }
}
