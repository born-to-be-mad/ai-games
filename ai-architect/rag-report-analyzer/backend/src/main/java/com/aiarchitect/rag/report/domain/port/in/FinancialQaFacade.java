package com.aiarchitect.rag.report.domain.port.in;

import com.aiarchitect.rag.report.domain.model.QaAnswer;

/**
 * Inbound port (use case): answer a financial question from ingested report documents.
 */
public interface FinancialQaFacade {

    /**
     * Retrieves relevant document chunks for the given question/context and generates
     * a grounded answer using the configured LLM.
     *
     * @param question natural-language financial question
     * @param ticker   company ticker to scope the search (e.g. "AAPL")
     * @param year     report year to scope the search
     * @return {@link QaAnswer} containing the answer and the source chunks used
     */
    QaAnswer ask(String question, String ticker, int year);
}
