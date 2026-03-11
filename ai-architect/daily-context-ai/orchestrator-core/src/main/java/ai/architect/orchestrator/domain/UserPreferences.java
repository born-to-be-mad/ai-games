package ai.architect.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    /** Comma-separated provider names, e.g. "openmeteo,weatherapi" */
    @Column(name = "default_weather_providers")
    private String defaultWeatherProviders;

    /** Comma-separated source names, e.g. "gnews,thenewsapi" */
    @Column(name = "default_news_sources")
    private String defaultNewsSources;

    /** UI theme: "light" or "dark" */
    @Builder.Default
    @Column(nullable = false)
    private String theme = "light";
}
