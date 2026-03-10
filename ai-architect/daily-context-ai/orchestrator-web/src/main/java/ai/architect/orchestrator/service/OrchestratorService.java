package ai.architect.orchestrator.service;

import ai.architect.orchestrator.agent.AgentResult;
import ai.architect.orchestrator.agent.NewsAgent;
import ai.architect.orchestrator.agent.QueryIntent;
import ai.architect.orchestrator.agent.WeatherAgent;
import lombok.RequiredArgsConstructor;
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
 *
 * Queries that need neither weather nor news are answered directly by the LLM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ProviderConfigService providerConfigService;
    private final WeatherAgent weatherAgent;
    private final NewsAgent newsAgent;
    private final AgentCoordinationService coordinationService;

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
                    .system("You are a helpful assistant. Answer the user's question clearly and concisely.")
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
            tasks.add(() -> weatherAgent.execute(weatherQuery, weatherProviders));
        }

        if (intent.needsNews()) {
            String newsQuery = intent.newsQuery().isBlank() ? userQuery : intent.newsQuery();
            tasks.add(() -> newsAgent.execute(newsQuery, newsSources));
        }

        // Step 4 — Run agents in parallel on virtual threads
        List<AgentResult> results = coordinationService.runParallel(tasks);

        // Step 5 — Synthesize into a final answer
        return synthesize(userQuery, results, chatClient);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private QueryIntent analyzeIntent(String query, ChatClient chatClient) {
        BeanOutputConverter<QueryIntent> converter = new BeanOutputConverter<>(QueryIntent.class);
        String response = chatClient.prompt()
                .system("""
                        You are a query intent classifier.
                        Analyze the user's message and return a JSON object with exactly these fields:
                        - needsWeather (boolean): true if the query asks about weather, temperature, or forecast
                        - needsNews (boolean): true if the query asks about news, events, or recent developments
                        - location (string): city or region for weather queries; empty string if not needed
                        - newsQuery (string): concise search terms for news queries; empty string if not needed
                        Respond ONLY with valid JSON. No markdown. No explanation.
                        """ + converter.getFormat())
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

        return chatClient.prompt()
                .system("""
                        You are a helpful assistant. Synthesize the provided information
                        into a clear, well-structured answer to the user's question.
                        Use markdown formatting where appropriate.
                        Do not mention internal agent names or system details.
                        """)
                .user("User question: " + originalQuery + "\n\nInformation gathered:\n" + context)
                .call()
                .content();
    }
}
