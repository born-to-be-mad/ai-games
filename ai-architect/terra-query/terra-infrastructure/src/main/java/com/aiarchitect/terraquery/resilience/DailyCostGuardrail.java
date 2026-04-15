package com.aiarchitect.terraquery.resilience;

import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coarse daily cost cap guardrail using deterministic estimate.
 */
@Component
public class DailyCostGuardrail {

    private final BigDecimal dailyCapUsd;
    private final AtomicReference<LocalDate> currentDay = new AtomicReference<>(LocalDate.now());
    private final AtomicReference<BigDecimal> spentUsd = new AtomicReference<>(BigDecimal.ZERO);

    public DailyCostGuardrail(AgentGuardrailsConfig guardrails) {
        this.dailyCapUsd = guardrails.dailyCostCapUsd();
    }

    public synchronized boolean canProcess() {
        rolloverIfNeeded();
        return spentUsd.get().compareTo(dailyCapUsd) < 0;
    }

    public synchronized void recordUsage(String answer, int toolCalls) {
        rolloverIfNeeded();
        BigDecimal estimated = estimateCost(answer, toolCalls);
        spentUsd.set(spentUsd.get().add(estimated));
    }

    private BigDecimal estimateCost(String answer, int toolCalls) {
        int answerTokens = Math.max(1, (answer != null ? answer.length() : 0) / 4);
        // conservative coarse estimate: response tokens + per-tool overhead
        BigDecimal tokenCost = BigDecimal.valueOf(answerTokens).multiply(new BigDecimal("0.000005"));
        BigDecimal toolOverhead = BigDecimal.valueOf(toolCalls).multiply(new BigDecimal("0.0005"));
        return tokenCost.add(toolOverhead);
    }

    private void rolloverIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay.get())) {
            currentDay.set(today);
            spentUsd.set(BigDecimal.ZERO);
        }
    }
}
