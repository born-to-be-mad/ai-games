package ai.architect.orchestrator.dto;

public record UserPreferencesDTO(
        String clientId,
        String defaultWeatherProviders,
        String defaultNewsSources,
        String theme
) {
}
