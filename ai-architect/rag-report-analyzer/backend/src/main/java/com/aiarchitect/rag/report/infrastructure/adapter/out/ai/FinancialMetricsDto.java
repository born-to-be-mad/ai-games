package com.aiarchitect.rag.report.infrastructure.adapter.out.ai;

import lombok.Data;

/**
 * DTO for Spring AI structured output ({@code ChatClient.call().entity(FinancialMetricsDto.class)}).
 *
 * <p>Spring AI's {@code BeanOutputConverter} generates a JSON schema from this class,
 * injects it into the prompt as format instructions, and parses the LLM response.
 * Kept in the infrastructure layer to avoid Jackson/Spring AI dependencies in the domain.
 */
@Data
public class FinancialMetricsDto {

    /** Total revenue in millions USD. Null if not found. */
    private Double revenue;

    /** Net income in millions USD. Null if not found. */
    private Double netIncome;

    /** Diluted earnings per share. Null if not found. */
    private Double eps;

    /** Operating cash flow in millions USD. Null if not found. */
    private Double operatingCashFlow;

    /** Total debt to total equity ratio. Null if not found. */
    private Double debtToEquity;
}
