package com.aiarchitect.rag.report.domain.model.eval;

import java.util.List;

/**
 * Aggregated RAG evaluation report for a single configuration run.
 *
 * @param topK                    number of context chunks retrieved per question
 * @param results                 per-question evaluation results
 * @param avgContextPrecision     mean context precision across all questions
 * @param avgContextRecall        mean context recall across all questions
 * @param avgFaithfulness         mean faithfulness across all questions
 * @param avgAnswerRelevance      mean answer relevance across all questions
 * @param avgOverall              mean of all four metrics
 * @param evaluatedAt             ISO-8601 timestamp of the run
 */
public record EvalReport(
        int topK,
        List<QuestionEvalResult> results,
        double avgContextPrecision,
        double avgContextRecall,
        double avgFaithfulness,
        double avgAnswerRelevance,
        double avgOverall,
        String evaluatedAt
) {
}
