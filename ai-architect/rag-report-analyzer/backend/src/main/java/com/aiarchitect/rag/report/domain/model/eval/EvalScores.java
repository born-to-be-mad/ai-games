package com.aiarchitect.rag.report.domain.model.eval;

/**
 * RAGAS-inspired evaluation scores for a single RAG pipeline response.
 *
 * <p>All scores are in the range [0.0, 1.0] where 1.0 is best.
 *
 * @param contextPrecision  fraction of retrieved chunks that are relevant to the question
 * @param contextRecall     fraction of expected-answer facts present in retrieved context
 * @param faithfulness      degree to which the generated answer is grounded in the context (no hallucinations)
 * @param answerRelevance   degree to which the generated answer addresses the question
 */
public record EvalScores(
        double contextPrecision,
        double contextRecall,
        double faithfulness,
        double answerRelevance
) {
    /** Average of all four metrics. */
    public double overall() {
        return (contextPrecision + contextRecall + faithfulness + answerRelevance) / 4.0;
    }
}
