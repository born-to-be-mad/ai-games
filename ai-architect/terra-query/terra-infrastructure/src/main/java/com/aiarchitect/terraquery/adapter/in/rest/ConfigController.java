package com.aiarchitect.terraquery.adapter.in.rest;

import com.aiarchitect.terraquery.api.ConfigApi;
import com.aiarchitect.terraquery.api.model.AgentConfig;
import com.aiarchitect.terraquery.adapter.in.rest.dto.RuntimeConfigResponse;
import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import com.aiarchitect.terraquery.resilience.DailyCostGuardrail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConfigController implements ConfigApi {

    private final AgentGuardrailsConfig guardrails;
    private final DailyCostGuardrail dailyCostGuardrail;

    @Value("${terra-query.ai.provider:openai}")
    private String provider;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o}")
    private String openAiModel;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}")
    private String anthropicModel;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaModel;

    @Value("${terra-query.mcp.embedding-model:bge-base-en-v1.5}")
    private String embeddingModel;

    @Override
    public ResponseEntity<AgentConfig> getConfig() {
        AgentConfig config = new AgentConfig()
                .maxSupervisorDelegations(guardrails.maxSupervisorDelegations())
                .maxRetrievalToolCalls(guardrails.maxRetrievalToolCalls())
                .maxAnalysisToolCalls(guardrails.maxAnalysisToolCalls())
                .contextWindowStrategy(AgentConfig.ContextWindowStrategyEnum
                        .fromValue(guardrails.contextWindowStrategy().name()))
                .slidingWindowSize(guardrails.slidingWindowSize())
                .maxQueriesPerMinute(guardrails.maxQueriesPerMinute())
                .agentTimeoutSeconds(guardrails.agentTimeoutSeconds());
        return ResponseEntity.ok(config);
    }

    @GetMapping("/api/v1/config/runtime")
    public ResponseEntity<RuntimeConfigResponse> getRuntimeConfig() {
        RuntimeConfigResponse response = new RuntimeConfigResponse(
                provider,
                resolveModel(provider),
                embeddingModel,
                guardrails.contextWindowStrategy().name(),
                guardrails.maxQueriesPerMinute(),
                dailyCostGuardrail.dailyCapUsd(),
                dailyCostGuardrail.spentUsd(),
                dailyCostGuardrail.remainingUsd()
        );
        return ResponseEntity.ok(response);
    }

    private String resolveModel(String activeProvider) {
        return switch (activeProvider) {
            case "anthropic" -> anthropicModel;
            case "ollama" -> ollamaModel;
            default -> openAiModel;
        };
    }
}
