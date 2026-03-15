package com.aiarchitect.rag.report.domain.model.eval;

/**
 * A single manually verified question–answer pair from the golden evaluation dataset.
 *
 * @param question        the evaluation question
 * @param expectedAnswer  the ground-truth answer verified against the source document
 * @param ticker          company ticker used to scope the similarity search
 * @param year            fiscal year used to scope the similarity search
 * @param quarter         reporting period (e.g. "Annual", "Q4")
 */
public record GoldenQaItem(
        String question,
        String expectedAnswer,
        String ticker,
        int year,
        String quarter
) {
}
