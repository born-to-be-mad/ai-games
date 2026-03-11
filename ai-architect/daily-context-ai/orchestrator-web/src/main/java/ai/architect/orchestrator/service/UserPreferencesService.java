package ai.architect.orchestrator.service;

import ai.architect.orchestrator.domain.UserPreferences;
import ai.architect.orchestrator.dto.UserPreferencesDTO;
import ai.architect.orchestrator.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    /** Returns existing preferences for the given clientId, or creates defaults on first call. */
    public UserPreferencesDTO getOrCreate(String clientId) {
        UserPreferences prefs = repository.findByClientId(clientId)
                .orElseGet(() -> {
                    log.info("Creating default preferences for clientId={}", clientId);
                    return repository.save(UserPreferences.builder()
                            .clientId(clientId)
                            .defaultWeatherProviders("")
                            .defaultNewsSources("")
                            .theme("light")
                            .build());
                });
        return toDTO(prefs);
    }

    @Transactional
    public UserPreferencesDTO save(String clientId, UserPreferencesDTO dto) {
        UserPreferences prefs = repository.findByClientId(clientId)
                .orElseGet(() -> UserPreferences.builder().clientId(clientId).build());

        if (dto.defaultWeatherProviders() != null) {
            prefs.setDefaultWeatherProviders(dto.defaultWeatherProviders());
        }
        if (dto.defaultNewsSources() != null) {
            prefs.setDefaultNewsSources(dto.defaultNewsSources());
        }
        if (dto.theme() != null) {
            prefs.setTheme(dto.theme());
        }

        UserPreferences saved = repository.save(prefs);
        log.info("Saved preferences for clientId={}", clientId);
        return toDTO(saved);
    }

    @Transactional
    public void delete(String clientId) {
        repository.deleteByClientId(clientId);
        log.info("Deleted preferences for clientId={}", clientId);
    }

    private UserPreferencesDTO toDTO(UserPreferences p) {
        return new UserPreferencesDTO(p.getClientId(), p.getDefaultWeatherProviders(),
                p.getDefaultNewsSources(), p.getTheme());
    }
}
