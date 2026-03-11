package ai.architect.orchestrator.controller;

import ai.architect.orchestrator.dto.UserPreferencesDTO;
import ai.architect.orchestrator.service.UserPreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    @GetMapping("/{clientId}")
    public ResponseEntity<UserPreferencesDTO> getPreferences(@PathVariable String clientId) {
        return ResponseEntity.ok(userPreferencesService.getOrCreate(clientId));
    }

    @PutMapping("/{clientId}")
    public ResponseEntity<UserPreferencesDTO> savePreferences(
            @PathVariable String clientId,
            @RequestBody UserPreferencesDTO dto) {
        return ResponseEntity.ok(userPreferencesService.save(clientId, dto));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> deletePreferences(@PathVariable String clientId) {
        userPreferencesService.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
