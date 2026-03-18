package com.aiarchitect.rag.report.infrastructure.adapter.out.eval;

import com.aiarchitect.rag.report.domain.model.LlmCallException;
import com.aiarchitect.rag.report.domain.model.eval.EvalScores;
import com.aiarchitect.rag.report.domain.port.out.EvalJudgePort;
import com.aiarchitect.rag.report.infrastructure.adapter.out.ai.LlmTokenMetrics;
import com.aiarchitect.rag.report.infrastructure.props.AiProviderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Outbound adapter: uses the active LLM to compute all four RAG evaluation scores in a single call.
 *
 * <p>Uses Spring AI's {@code ChatClient.call().chatResponse()} to access provider token usage metadata
 * and parse judge scores from response text.
 *
 * <p>Parsing strategy:
 * <ol>
 *   <li>Strict JSON → {@link EvalScoresDto} via Jackson</li>
 *   <li>If strict parse fails, salvage scores with regex from partial/truncated output</li>
 * </ol>
 * System prompt loaded from {@code classpath:prompts/eval-judge.st}.
 *
 * <h3>Resilience</h3>
 * Retries up to 3 times with exponential backoff (1s → 2s → 4s) on any exception.
 * After all retries fail, returns zeroed scores to keep matrix runs progressing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmEvalJudgeAdapter implements EvalJudgePort {

    private static final Pattern CONTEXT_PRECISION_PATTERN = Pattern.compile("\"contextPrecision\"\\s*:\\s*([-+]?\\d*\\.?\\d+)");
    private static final Pattern CONTEXT_RECALL_PATTERN = Pattern.compile("\"contextRecall\"\\s*:\\s*([-+]?\\d*\\.?\\d+)");
    private static final Pattern FAITHFULNESS_PATTERN = Pattern.compile("\"faithfulness\"\\s*:\\s*([-+]?\\d*\\.?\\d+)");
    private static final Pattern ANSWER_RELEVANCE_PATTERN = Pattern.compile("\"answerRelevance\"\\s*:\\s*([-+]?\\d*\\.?\\d+)");

    private final Map<String, ChatClient> chatClientsByProvider;
    private final AiProviderProperties aiProviderProperties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

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

        List<Document> chunks = retrievedChunks != null ? retrievedChunks : List.of();

        String context = chunks.stream()
                .map(doc -> "Chunk [page=%s]:\n%s".formatted(
                        doc.getMetadata().getOrDefault("page_number", "?"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("Judging answer for question='{}' with {} context chunks via {}",
                question, chunks.size(), provider);

        ChatResponse chatResponse = Timer.builder("llm.call.duration")
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
                                .param("chunkCount", String.valueOf(chunks.size()))
                                .param("context", context.isEmpty() ? "(no chunks retrieved)" : context))
                        .call()
                        .chatResponse());

        LlmTokenMetrics.record(meterRegistry, provider, "eval_judge", chatResponse);
        EvalScoresDto dto = parseEvalScores(chatResponse, provider);
        if (dto == null) {
            throw new LlmCallException("LLM returned empty structured response for evaluation");
        }

        double precision = safeScore(dto.getContextPrecision());
        double recall = safeScore(dto.getContextRecall());
        double faithfulness = safeScore(dto.getFaithfulness());
        double relevance = safeScore(dto.getAnswerRelevance());

        log.debug("Judge scores: precision={} recall={} faithfulness={} relevance={}",
                precision, recall, faithfulness, relevance);

        return new EvalScores(precision, recall, faithfulness, relevance);
    }

    @Recover
    public EvalScores judgeFallback(Exception ex, String question, String expectedAnswer,
                                    String generatedAnswer, List<Document> retrievedChunks) {
        log.error("Eval judge failed after 3 retries for question='{}'", question, ex);
        Counter.builder("llm.call.fallback")
                .tag("provider", aiProviderProperties.active())
                .tag("operation", "eval_judge")
                .register(meterRegistry)
                .increment();
        // Keep matrix evaluation running even when one judge response cannot be parsed.
        return new EvalScores(0.0, 0.0, 0.0, 0.0);
    }

    /** Clamps null or out-of-range values to [0.0, 1.0]. */
    private static double safeScore(Double value) {
        if (value == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private EvalScoresDto parseEvalScores(ChatResponse chatResponse, String provider) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }

        String content = chatResponse.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(content, EvalScoresDto.class);
        } catch (IOException ex) {
            Counter.builder("llm.call.parse.failure")
                    .tag("provider", provider)
                    .tag("operation", "eval_judge")
                    .register(meterRegistry)
                    .increment();
            log.warn("Failed to parse eval judge JSON strictly; attempting salvage parse. content='{}'",
                    truncate(content, 500), ex);
            return salvageEvalScores(content);
        }
    }

    private static EvalScoresDto salvageEvalScores(String content) {
        EvalScoresDto dto = new EvalScoresDto();
        dto.setContextPrecision(extractNumber(CONTEXT_PRECISION_PATTERN, content));
        dto.setContextRecall(extractNumber(CONTEXT_RECALL_PATTERN, content));
        dto.setFaithfulness(extractNumber(FAITHFULNESS_PATTERN, content));
        dto.setAnswerRelevance(extractNumber(ANSWER_RELEVANCE_PATTERN, content));

        boolean hasAnyValue = dto.getContextPrecision() != null
                || dto.getContextRecall() != null
                || dto.getFaithfulness() != null
                || dto.getAnswerRelevance() != null;
        return hasAnyValue ? dto : null;
    }

    private static Double extractNumber(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }
}
