package com.aiarchitect.rag.report.infrastructure.adapter.in.rest;

import com.aiarchitect.rag.report.domain.model.QaAnswer;
import com.aiarchitect.rag.report.domain.port.in.FinancialQaFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter: exposes the financial Q&A use case over HTTP.
 *
 * <pre>
 * GET /api/v1/qa?question=What+was+net+income%3F&ticker=NVDA&year=2025
 *
 * Response:
 * {
 *   "answer": "Net income for fiscal 2025 was $72.9B ...",
 *   "sources": [
 *     { "chunkId": "abc123", "text": "Net income was...", "pageNumber": "42" },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * <p>Requires documents to be ingested first (Phase 1+2 pipeline).
 * Returns an empty sources list with an explanatory answer when no
 * matching documents are found in the vector store.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
public class QaController {

    private final FinancialQaFacade financialQaFacade;

    @GetMapping
    public QaAnswer ask(
            @RequestParam String question,
            @RequestParam String ticker,
            @RequestParam int year) {
        log.info("GET /api/v1/qa — ticker={}, year={}, question='{}'", ticker, year, question);
        return financialQaFacade.ask(question, ticker, year);
    }
}
