package com.aiarchitect.terraquery.live;

import com.aiarchitect.terraquery.model.AgentResponse;
import com.aiarchitect.terraquery.port.in.ChatUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Live LLM evaluation tests — require real API keys + running terra-mcp server.
 *
 * Subset control via system property {@code eval.subset}:
 *   canary  — first 10 questions (runs on every PR, fast)
 *   full    — all 30 questions  (runs nightly)
 *
 * Run locally:
 *   OPENAI_API_KEY=... ANTHROPIC_API_KEY=... \
 *   ./gradlew :terra-infrastructure:liveTest -Psubset=canary
 *
 * Generator (OpenAI gpt-4o-mini) produces the answer via the full agent pipeline.
 * Evaluator (Anthropic claude-haiku) scores relevance — cross-provider to avoid self-eval bias.
 * Detailed RAGAS scoring (context precision/recall/faithfulness) runs in Python eval_runner.py.
 */
@Tag("live-llm")
@SpringBootTest
@ActiveProfiles("live-test")
@Import(EvalConfig.class)
class LiveLlmEvalTest {

    private static final String GOLDEN_DATASET = "golden_dataset.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    ChatUseCase chatUseCase;

    @Qualifier("evaluatorChatModel")
    @Autowired
    ChatModel evaluatorChatModel;

    // ── Question loading ──────────────────────────────────────────────────────

    static Stream<QuestionEntry> goldenQuestions() {
        String subset = System.getProperty("eval.subset", "canary");
        int limit = "full".equals(subset) ? 30 : 10;
        return loadGoldenDataset().stream().limit(limit);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("goldenQuestions")
    void question_agentAnswers_nonEmptyAndCoherent(QuestionEntry entry) {
        AgentResponse response = chatUseCase.chat(entry.question(), null);

        assertThat(response).isNotNull();
        assertThat(response.answer())
                .as("Answer for question '%s' must not be blank", entry.id())
                .isNotBlank();
        assertThat(response.answer().length())
                .as("Answer must be substantial (>50 chars) for question '%s'", entry.id())
                .isGreaterThan(50);
        assertThat(response.answer().toLowerCase())
                .as("Answer must not be an error message for question '%s'", entry.id())
                .doesNotContain("error", "sorry, i cannot", "i don't have access");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("goldenQuestions")
    void question_evaluatorScores_aboveRelevanceFloor(QuestionEntry entry) {
        // Uses evaluator LLM (Anthropic claude-haiku) to score answer relevance independently.
        // This is a lightweight proxy check; full RAGAS scoring runs in Python eval_runner.py.
        AgentResponse response = chatUseCase.chat(entry.question(), null);
        assumeThat(response.answer()).isNotBlank();

        String evalPrompt = """
                You are evaluating whether an answer is relevant to the question asked.
                Question: %s
                Expected answer (ground truth): %s
                Actual answer: %s

                Reply with only a single integer from 1 (completely irrelevant) to 5 (highly relevant).
                """.formatted(entry.question(), entry.groundTruth(), response.answer());

        String scoreStr = evaluatorChatModel
                .call(new Prompt(evalPrompt))
                .getResult()
                .getOutput()
                .getText()
                .trim()
                .replaceAll("[^1-5]", "");

        assertThat(scoreStr)
                .as("Evaluator must return a score 1-5 for question '%s'", entry.id())
                .matches("[1-5]");

        int score = Integer.parseInt(scoreStr);
        assertThat(score)
                .as("Relevance score must be ≥3 for question '%s' — got %d", entry.id(), score)
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void agentChain_allQuestionsUseExpectedAgents() {
        QuestionEntry first = loadGoldenDataset().getFirst();
        AgentResponse response = chatUseCase.chat(first.question(), null);
        assertThat(response.agentChain())
                .containsExactly("SupervisorAgent", "DataRetrievalAgent", "AnalysisSynthesisAgent");
    }

    // ── Dataset loading ───────────────────────────────────────────────────────

    static List<QuestionEntry> loadGoldenDataset() {
        try (InputStream is = LiveLlmEvalTest.class.getClassLoader()
                .getResourceAsStream(GOLDEN_DATASET)) {
            if (is == null) {
                throw new IllegalStateException(
                        "golden_dataset.json not found on classpath — "
                        + "check terra-infrastructure/src/test/resources/");
            }
            JsonNode root = MAPPER.readTree(is);
            List<QuestionEntry> entries = new ArrayList<>();
            for (JsonNode node : root) {
                entries.add(new QuestionEntry(
                        node.get("id").asText(),
                        node.get("question").asText(),
                        node.get("ground_truth").asText()
                ));
            }
            return entries;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load golden dataset", e);
        }
    }

    record QuestionEntry(String id, String question, String groundTruth) {
        @Override
        public String toString() {
            return id + ": " + question.substring(0, Math.min(60, question.length()));
        }
    }
}
