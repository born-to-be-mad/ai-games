package com.aiarchitect.rag.report.infrastructure.adapter.out.eval;

import com.aiarchitect.rag.report.domain.model.LlmCallException;
import com.aiarchitect.rag.report.domain.model.eval.EvalScores;
import com.aiarchitect.rag.report.domain.port.out.EvalJudgePort;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Outbound adapter: uses the active LLM to compute all four RAG evaluation scores in a single call.
 *
 * <p>Uses Spring AI's {@code ChatClient.call().entity(EvalScoresDto.class)} for structured output.
 * System prompt loaded from {@code classpath:prompts/eval-judge.st}.
 *
 * <h3>Resilience</h3>
 * Retries up to 3 times with exponential backoff (1s → 2s → 4s) on any exception.
 * After all retries fail, throws {@link LlmCallException}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmEvalJudgeAdapter implements EvalJudgePort {

    private final Map<String, ChatClient> chatClientsByProvider;
    private final AiProviderProperties aiProviderProperties;
    private final MeterRegistry meterRegistry;

    @Value("classpath:prompts/eval-judge.st")
    private Resource systemPromptResource;

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Override
    public EvalScores judge(
            String question,
            String expectedAnswer,
            String generatedAnswer,
            List<Document> retrievedChunks) {

        String provider = aiProviderProperties.active();
        ChatClient client = chatClientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException(
                    "LLM provider '%s' is not configured. Available: %s"
                            .formatted(provider, chatClientsByProvider.keySet()));
        }

        String context = retrievedChunks.stream()
                .map(doc -> "Chunk [page=%s]:\n%s".formatted(
                        doc.getMetadata().getOrDefault("page_number", "?"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("Judging answer for question='{}' with {} context chunks via {}",
                question, retrievedChunks.size(), provider);

        EvalScoresDto dto = Timer.builder("llm.call.duration")
                .tag("provider", provider)
                .tag("operation", "eval_judge")
                .register(meterRegistry)
                .record(() -> client.prompt()
                        .system(s -> s.text(systemPromptResource))
                        .user(u -> u.text("""
                                Question: {question}

                                Expected answer (ground truth):
                                {expectedAnswer}

                                Generated answer (RAG pipeline output):
                                {generatedAnswer}

                                Retrieved context ({chunkCount} chunks):
                                {context}
                                """)
                                .param("question", question)
                                .param("expectedAnswer", expectedAnswer)
                                .param("generatedAnswer", generatedAnswer)
                                .param("chunkCount", String.valueOf(retrievedChunks.size()))
                                .param("context", context.isEmpty() ? "(no chunks retrieved)" : context))
                        .call()
                        .entity(EvalScoresDto.class));

        double precision = safeScore(dto.getContextPrecision());
        double recall = safeScore(dto.getContextRecall());
        double faithfulness = safeScore(dto.getFaithfulness());
        double relevance = safeScore(dto.getAnswerRelevance());

        log.debug("Judge scores: precision={:.2f} recall={:.2f} faithfulness={:.2f} relevance={:.2f}",
                precision, recall, faithfulness, relevance);

        return new EvalScores(precision, recall, faithfulness, relevance);
    }

    @Recover
    public EvalScores judgeFallback(Exception ex, String question, String expectedAnswer,
                                    String generatedAnswer, List<Document> retrievedChunks) {
        log.error("Eval judge failed after 3 retries for question='{}'", question, ex);
        throw new LlmCallException("LLM unavailable after retries: " + ex.getMessage(), ex);
    }

    /** Clamps null or out-of-range values to [0.0, 1.0]. */
    private static double safeScore(Double value) {
        if (value == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
