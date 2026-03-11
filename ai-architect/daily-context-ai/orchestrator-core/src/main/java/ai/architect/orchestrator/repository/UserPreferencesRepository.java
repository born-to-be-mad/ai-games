package ai.architect.orchestrator.repository;

import ai.architect.orchestrator.domain.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {

    Optional<UserPreferences> findByClientId(String clientId);

    void deleteByClientId(String clientId);
}
