package ai.architect.orchestrator.controller;

import ai.architect.orchestrator.service.ChatClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ChatClientFactory chatClientFactory;

    @GetMapping("/providers")
    public ResponseEntity<List<String>> getProviders() {
        return ResponseEntity.ok(chatClientFactory.getAvailableProviders());
    }
}
