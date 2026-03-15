package com.aiarchitect.rag.report.domain.model.eval;

/**
 * Evaluation result for a single golden Q&A item.
 *
 * @param question              the evaluation question
 * @param expectedAnswer        ground-truth answer from the golden dataset
 * @param generatedAnswer       the RAG pipeline's answer for this run
 * @param retrievedChunksCount  number of context chunks retrieved
 * @param scores                the four RAGAS-inspired metric scores
 */
public record QuestionEvalResult(
        String question,
        String expectedAnswer,
        String generatedAnswer,
        int retrievedChunksCount,
        EvalScores scores
) {
}
