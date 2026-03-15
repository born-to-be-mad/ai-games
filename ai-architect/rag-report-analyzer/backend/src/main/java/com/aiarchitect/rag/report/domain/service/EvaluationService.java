package com.aiarchitect.rag.report.domain.service;

import com.aiarchitect.rag.report.domain.model.eval.EvalReport;
import com.aiarchitect.rag.report.domain.model.eval.EvalScores;
import com.aiarchitect.rag.report.domain.model.eval.GoldenQaItem;
import com.aiarchitect.rag.report.domain.model.eval.QuestionEvalResult;
import com.aiarchitect.rag.report.domain.port.in.EvaluationFacade;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.EvalJudgePort;
import com.aiarchitect.rag.report.domain.port.out.GoldenDatasetPort;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Domain service: orchestrates the RAG evaluation pipeline.
 *
 * <p>For each golden Q&A item, this service:
 * <ol>
 *   <li>Retrieves top-k context chunks via {@link DocumentStorePort}</li>
 *   <li>Generates an answer via {@link LanguageModelPort}</li>
 *   <li>Scores the result via {@link EvalJudgePort} (LLM-as-judge)</li>
 * </ol>
 * Aggregates per-question scores into an {@link EvalReport}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService implements EvaluationFacade {

    public static final List<Integer> MATRIX_TOP_K_VALUES = List.of(3, 5, 10);

    private final DocumentStorePort documentStorePort;
    private final LanguageModelPort languageModelPort;
    private final EvalJudgePort evalJudgePort;
    private final GoldenDatasetPort goldenDatasetPort;

    @Override
    public EvalReport evaluate(int topK) {
        List<GoldenQaItem> items = goldenDatasetPort.loadAll();
        log.info("Starting RAG evaluation: {} golden items, topK={}", items.size(), topK);

        List<QuestionEvalResult> results = items.stream()
                .map(item -> evaluateItem(item, topK))
                .toList();

        return buildReport(topK, results);
    }

    @Override
    public List<EvalReport> evaluateMatrix() {
        return MATRIX_TOP_K_VALUES.stream()
                .map(this::evaluate)
                .toList();
    }

    private QuestionEvalResult evaluateItem(GoldenQaItem item, int topK) {
        log.debug("Evaluating: '{}' (ticker={}, year={})", item.question(), item.ticker(), item.year());

        Map<String, Object> filter = Map.of("ticker", item.ticker(), "year", item.year());
        List<Document> chunks = documentStorePort.similaritySearch(item.question(), topK, filter);
        String generatedAnswer = languageModelPort.answer(item.question(), chunks);
        EvalScores scores = evalJudgePort.judge(item.question(), item.expectedAnswer(), generatedAnswer, chunks);

        log.debug("Scores — precision={:.2f} recall={:.2f} faithful={:.2f} relevance={:.2f}",
                scores.contextPrecision(), scores.contextRecall(),
                scores.faithfulness(), scores.answerRelevance());

        return new QuestionEvalResult(
                item.question(),
                item.expectedAnswer(),
                generatedAnswer,
                chunks.size(),
                scores);
    }

    private static EvalReport buildReport(int topK, List<QuestionEvalResult> results) {
        double avgPrecision = average(results, r -> r.scores().contextPrecision());
        double avgRecall = average(results, r -> r.scores().contextRecall());
        double avgFaithfulness = average(results, r -> r.scores().faithfulness());
        double avgRelevance = average(results, r -> r.scores().answerRelevance());
        double avgOverall = (avgPrecision + avgRecall + avgFaithfulness + avgRelevance) / 4.0;

        return new EvalReport(
                topK, results,
                avgPrecision, avgRecall, avgFaithfulness, avgRelevance, avgOverall,
                Instant.now().toString());
    }

    @FunctionalInterface
    private interface ScoreExtractor {
        double extract(QuestionEvalResult r);
    }

    private static double average(List<QuestionEvalResult> results, ScoreExtractor extractor) {
        if (results.isEmpty()) return 0.0;
        return results.stream()
                .mapToDouble(extractor::extract)
                .average()
                .orElse(0.0);
    }
}
