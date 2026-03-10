package ai.architect.orchestrator.agent;

/**
 * LLM-analyzed intent of the user's query.
 *
 * @param needsWeather true when the query asks about current weather or forecast
 * @param needsNews    true when the query asks about news or recent events
 * @param location     city/region extracted for weather queries; empty string if not applicable
 * @param newsQuery    search terms extracted for news queries; empty string if not applicable
 */
public record QueryIntent(
        boolean needsWeather,
        boolean needsNews,
        String location,
        String newsQuery
) {}
