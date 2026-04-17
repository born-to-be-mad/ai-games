package com.aiarchitect.terraquery.adapter.in.rest.dto;

import java.math.BigDecimal;

public record RuntimeConfigResponse(
        String provider,
        String model,
        String embeddingModel,
        String contextWindowStrategy,
        int maxQueriesPerMinute,
        BigDecimal dailyCostCapUsd,
        BigDecimal dailyCostSpentUsd,
        BigDecimal dailyCostRemainingUsd
) {
}
