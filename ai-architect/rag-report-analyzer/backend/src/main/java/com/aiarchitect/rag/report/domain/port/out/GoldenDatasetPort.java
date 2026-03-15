package com.aiarchitect.rag.report.domain.port.out;

import com.aiarchitect.rag.report.domain.model.eval.GoldenQaItem;

import java.util.List;

/**
 * Outbound port: loads the manually verified golden Q&A dataset used for RAG evaluation.
 *
 * <p>The concrete adapter reads from {@code classpath:eval/golden-dataset.json}.
 */
public interface GoldenDatasetPort {

    /**
     * Returns all golden Q&A items from the dataset.
     */
    List<GoldenQaItem> loadAll();
}
