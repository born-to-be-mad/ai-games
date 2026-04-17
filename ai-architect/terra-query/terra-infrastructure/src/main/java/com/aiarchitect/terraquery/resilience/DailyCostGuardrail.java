package com.aiarchitect.terraquery.resilience;

import com.aiarchitect.terraquery.config.AgentGuardrailsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter estimatedCostCounter;

    public DailyCostGuardrail(AgentGuardrailsConfig guardrails, MeterRegistry meterRegistry) {
        this.dailyCapUsd = guardrails.dailyCostCapUsd();
        this.estimatedCostCounter = Counter.builder("terra_query_cost_estimate_usd_total")
                .description("Cumulative estimated LLM/tooling cost in USD")
                .register(meterRegistry);

        Gauge.builder("terra_query_cost_spent_usd", spentUsd, ref -> ref.get().doubleValue())
                .description("Estimated USD spent today")
                .register(meterRegistry);

        Gauge.builder("terra_query_cost_daily_cap_usd", dailyCapUsd, BigDecimal::doubleValue)
                .description("Configured daily cost cap in USD")
                .register(meterRegistry);

        Gauge.builder("terra_query_cost_remaining_usd", this, DailyCostGuardrail::remainingUsdAsDouble)
                .description("Estimated remaining daily budget in USD")
                .register(meterRegistry);
    }

    public synchronized boolean canProcess() {
        rolloverIfNeeded();
        return spentUsd.get().compareTo(dailyCapUsd) < 0;
    }

    public synchronized void recordUsage(String answer, int toolCalls) {
        rolloverIfNeeded();
        BigDecimal estimated = estimateCost(answer, toolCalls);
        spentUsd.set(spentUsd.get().add(estimated));
        estimatedCostCounter.increment(estimated.doubleValue());
    }

    public synchronized BigDecimal spentUsd() {
        rolloverIfNeeded();
        return spentUsd.get();
    }

    public BigDecimal dailyCapUsd() {
        return dailyCapUsd;
    }

    public synchronized BigDecimal remainingUsd() {
        rolloverIfNeeded();
        BigDecimal remaining = dailyCapUsd.subtract(spentUsd.get());
        return remaining.max(BigDecimal.ZERO);
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

    private double remainingUsdAsDouble() {
        return remainingUsd().doubleValue();
    }
}
