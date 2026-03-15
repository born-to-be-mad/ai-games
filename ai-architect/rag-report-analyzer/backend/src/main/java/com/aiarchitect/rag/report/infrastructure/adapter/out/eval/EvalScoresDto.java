package com.aiarchitect.rag.report.infrastructure.adapter.out.eval;

import lombok.Data;

/**
 * DTO for Spring AI structured output: LLM-as-judge returns all four RAGAS-inspired scores in one call.
 *
 * <p>Kept in infrastructure to avoid Jackson/Spring AI dependencies in the domain.
 */
@Data
public class EvalScoresDto {

    /** Fraction of retrieved chunks relevant to the question (0.0–1.0). */
    private Double contextPrecision;

    /** Fraction of expected-answer facts found in retrieved context (0.0–1.0). */
    private Double contextRecall;

    /** Degree to which generated answer is grounded in context with no hallucinations (0.0–1.0). */
    private Double faithfulness;

    /** Degree to which generated answer addresses the question (0.0–1.0). */
    private Double answerRelevance;
}
