package com.aiarchitect.rag.report.service;

import com.aiarchitect.rag.report.domain.model.eval.EvalReport;
import com.aiarchitect.rag.report.domain.model.eval.EvalScores;
import com.aiarchitect.rag.report.domain.model.eval.GoldenQaItem;
import com.aiarchitect.rag.report.domain.model.eval.QuestionEvalResult;
import com.aiarchitect.rag.report.domain.port.out.DocumentStorePort;
import com.aiarchitect.rag.report.domain.port.out.EvalJudgePort;
import com.aiarchitect.rag.report.domain.port.out.GoldenDatasetPort;
import com.aiarchitect.rag.report.domain.port.out.LanguageModelPort;
import com.aiarchitect.rag.report.domain.service.EvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("EvaluationService — RAG pipeline evaluation orchestration")
class EvaluationServiceTest {

    private DocumentStorePort documentStorePort;
    private LanguageModelPort languageModelPort;
    private EvalJudgePort evalJudgePort;
    private GoldenDatasetPort goldenDatasetPort;
    private EvaluationService service;

    private final GoldenQaItem item1 = new GoldenQaItem(
            "What was NVDA revenue?", "Revenue was $130.5B.", "NVDA", 2025, "Annual");
    private final GoldenQaItem item2 = new GoldenQaItem(
            "What was NVDA net income?", "Net income was $72.9B.", "NVDA", 2025, "Annual");

    private final EvalScores perfectScores = new EvalScores(1.0, 1.0, 1.0, 1.0);
    private final Document chunk = new Document("Revenue for FY2025 was $130.5B.");

    @BeforeEach
    void setUp() {
        documentStorePort = mock(DocumentStorePort.class);
        languageModelPort = mock(LanguageModelPort.class);
        evalJudgePort = mock(EvalJudgePort.class);
        goldenDatasetPort = mock(GoldenDatasetPort.class);
        service = new EvaluationService(documentStorePort, languageModelPort, evalJudgePort, goldenDatasetPort);
    }

    @Test
    @DisplayName("evaluate: runs retrieval + generation + judging for each golden item")
    void evaluateOrchestratesPipeline() {
        when(goldenDatasetPort.loadAll()).thenReturn(List.of(item1));
        when(documentStorePort.similaritySearch(anyString(), anyInt(), any())).thenReturn(List.of(chunk));
        when(languageModelPort.answer(anyString(), any())).thenReturn("Revenue was $130.5B.");
        when(evalJudgePort.judge(any(), any(), any(), any())).thenReturn(perfectScores);

        EvalReport report = service.evaluate(5);

        assertThat(report.topK()).isEqualTo(5);
        assertThat(report.results()).hasSize(1);
        verify(documentStorePort).similaritySearch(eq("What was NVDA revenue?"), eq(5), any());
        verify(languageModelPort).answer(eq("What was NVDA revenue?"), eq(List.of(chunk)));
        verify(evalJudgePort).judge(
                eq("What was NVDA revenue?"),
                eq("Revenue was $130.5B."),
                eq("Revenue was $130.5B."),
                eq(List.of(chunk)));
    }

    @Test
    @DisplayName("evaluate: aggregates scores correctly across multiple items")
    void evaluateAggregatesScores() {
        EvalScores scores1 = new EvalScores(0.8, 0.7, 0.9, 0.6);
        EvalScores scores2 = new EvalScores(0.6, 0.5, 0.7, 0.8);
        when(goldenDatasetPort.loadAll()).thenReturn(List.of(item1, item2));
        when(documentStorePort.similaritySearch(any(), anyInt(), any())).thenReturn(List.of(chunk));
        when(languageModelPort.answer(any(), any())).thenReturn("some answer");
        when(evalJudgePort.judge(any(), any(), any(), any()))
                .thenReturn(scores1)
                .thenReturn(scores2);

        EvalReport report = service.evaluate(5);

        assertThat(report.avgContextPrecision()).isEqualTo((0.8 + 0.6) / 2, within(0.001));
        assertThat(report.avgContextRecall()).isEqualTo((0.7 + 0.5) / 2, within(0.001));
        assertThat(report.avgFaithfulness()).isEqualTo((0.9 + 0.7) / 2, within(0.001));
        assertThat(report.avgAnswerRelevance()).isEqualTo((0.6 + 0.8) / 2, within(0.001));
        assertThat(report.avgOverall()).isCloseTo(
                ((0.8 + 0.7 + 0.9 + 0.6 + 0.6 + 0.5 + 0.7 + 0.8) / 8), within(0.001));
    }

    @Test
    @DisplayName("evaluate: passes correct metadata filter (ticker + year) to document store")
    void evaluatePassesCorrectFilter() {
        when(goldenDatasetPort.loadAll()).thenReturn(List.of(item1));
        when(documentStorePort.similaritySearch(any(), anyInt(), any())).thenReturn(List.of());
        when(languageModelPort.answer(any(), any())).thenReturn("I cannot answer.");
        when(evalJudgePort.judge(any(), any(), any(), any())).thenReturn(perfectScores);

        service.evaluate(3);

        verify(documentStorePort).similaritySearch(
                eq("What was NVDA revenue?"),
                eq(3),
                argThat(filter -> "NVDA".equals(filter.get("ticker")) && Integer.valueOf(2025).equals(filter.get("year"))));
    }

    @Test
    @DisplayName("evaluate: handles empty context gracefully (no chunks retrieved)")
    void evaluateHandlesEmptyContext() {
        when(goldenDatasetPort.loadAll()).thenReturn(List.of(item1));
        when(documentStorePort.similaritySearch(any(), anyInt(), any())).thenReturn(List.of());
        when(languageModelPort.answer(any(), any())).thenReturn("I cannot answer from context.");
        when(evalJudgePort.judge(any(), any(), any(), any()))
                .thenReturn(new EvalScores(0.0, 0.0, 1.0, 0.0));

        EvalReport report = service.evaluate(5);

        QuestionEvalResult result = report.results().getFirst();
        assertThat(result.retrievedChunksCount()).isZero();
        assertThat(result.scores().faithfulness()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("evaluateMatrix: runs evaluate for each of [3, 5, 10]")
    void evaluateMatrixRunsThreeConfigs() {
        when(goldenDatasetPort.loadAll()).thenReturn(List.of(item1));
        when(documentStorePort.similaritySearch(any(), anyInt(), any())).thenReturn(List.of(chunk));
        when(languageModelPort.answer(any(), any())).thenReturn("Revenue was $130.5B.");
        when(evalJudgePort.judge(any(), any(), any(), any())).thenReturn(perfectScores);

        List<EvalReport> reports = service.evaluateMatrix();

        assertThat(reports).hasSize(3);
        assertThat(reports.stream().map(EvalReport::topK).toList())
                .containsExactlyElementsOf(EvaluationService.MATRIX_TOP_K_VALUES);
    }

    @Test
    @DisplayName("EvalScores.overall() returns mean of four metrics")
    void evalScoresOverall() {
        EvalScores scores = new EvalScores(0.8, 0.6, 0.9, 0.7);
        assertThat(scores.overall()).isCloseTo((0.8 + 0.6 + 0.9 + 0.7) / 4.0, within(0.001));
    }

    // AssertJ offset helper
    private static org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
