package com.aiarchitect.rag.report.infrastructure.adapter.in.rest;

import com.aiarchitect.rag.report.domain.model.eval.EvalReport;
import com.aiarchitect.rag.report.domain.port.in.EvaluationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter: triggers RAG evaluation runs against the golden dataset.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/eval/run?topK=5} — single configuration run (default topK=5)</li>
 *   <li>{@code POST /api/v1/eval/run/matrix} — matrix run across topK=[3,5,10]</li>
 * </ul>
 *
 * <p>These are POST endpoints because they trigger expensive LLM calls (not idempotent reads).
 * Expect ~2–3 minutes for the full dataset with a remote LLM provider.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationFacade evaluationFacade;

    @PostMapping("/run")
    public ResponseEntity<EvalReport> run(
            @RequestParam(defaultValue = "5") int topK) {
        log.info("Starting evaluation run with topK={}", topK);
        EvalReport report = evaluationFacade.evaluate(topK);
        log.info("Evaluation complete: overall={:.3f} (precision={:.3f} recall={:.3f} faithful={:.3f} relevance={:.3f})",
                report.avgOverall(), report.avgContextPrecision(), report.avgContextRecall(),
                report.avgFaithfulness(), report.avgAnswerRelevance());
        return ResponseEntity.ok(report);
    }

    @PostMapping("/run/matrix")
    public ResponseEntity<List<EvalReport>> runMatrix() {
        log.info("Starting evaluation matrix run (topK=3,5,10)");
        List<EvalReport> reports = evaluationFacade.evaluateMatrix();
        return ResponseEntity.ok(reports);
    }
}
