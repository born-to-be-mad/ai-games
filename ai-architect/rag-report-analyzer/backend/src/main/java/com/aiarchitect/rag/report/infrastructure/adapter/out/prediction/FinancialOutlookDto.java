package com.aiarchitect.rag.report.infrastructure.adapter.out.prediction;

import lombok.Data;

import java.util.List;

/**
 * DTO for Spring AI structured output ({@code ChatClient.call().entity(FinancialOutlookDto.class)}).
 *
 * <p>Spring AI's {@code BeanOutputConverter} generates JSON schema from this class,
 * appends format instructions to the prompt, and parses the LLM response.
 * Kept in infrastructure to avoid LLM/Jackson dependencies in the domain.
 *
 * <p>The {@code methodology} field is not included here — each strategy appends it after parsing.
 */
@Data
public class FinancialOutlookDto {

    /** Qualitative description of the financial trend. */
    private String trend;

    /** Key risk factors (2–4 items). */
    private List<String> risks;

    /** Predicted revenue range for the next period, e.g. "$140B–$155B". */
    private String predictedRevenueRange;

    /** Confidence in the prediction, 0.0 (none) – 1.0 (very high). */
    private Double confidence;
}
