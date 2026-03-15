package com.aiarchitect.rag.report.domain.port.in;

import com.aiarchitect.rag.report.domain.model.eval.EvalReport;

import java.util.List;

/**
 * Inbound port: runs RAG quality evaluation against the golden dataset.
 */
public interface EvaluationFacade {

    /**
     * Evaluates the RAG pipeline against all golden Q&A items using the given {@code topK}.
     *
     * @param topK number of context chunks to retrieve per question
     * @return aggregated evaluation report
     */
    EvalReport evaluate(int topK);

    /**
     * Runs evaluation for each top_k in [3, 5, 10] and returns one report per configuration.
     * Used to generate the chunk_size × top_k comparison matrix in {@code EVALUATION.md}.
     */
    List<EvalReport> evaluateMatrix();
}
