package com.aiarchitect.terraquery.adapter.in.rest;

import com.aiarchitect.terraquery.api.ConfigApi;
import com.aiarchitect.terraquery.api.model.AgentConfig;
import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConfigController implements ConfigApi {

    private final AgentGuardrailsConfig guardrails;

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
}
