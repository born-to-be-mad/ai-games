package com.aiarchitect.rag.report.domain.port.out;

import com.aiarchitect.rag.report.domain.model.eval.EvalScores;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Outbound port: LLM-as-judge scoring of a single RAG pipeline response.
 *
 * <p>Computes all four RAGAS-inspired metrics in a single LLM call to minimise latency and cost.
 */
public interface EvalJudgePort {

    /**
     * Scores a RAG response against the golden expected answer.
     *
     * @param question         the evaluation question
     * @param expectedAnswer   ground-truth answer from the golden dataset
     * @param generatedAnswer  the RAG pipeline's answer
     * @param retrievedChunks  the context chunks passed to the LLM when generating the answer
     * @return four metric scores in [0.0, 1.0]
     */
    EvalScores judge(
            String question,
            String expectedAnswer,
            String generatedAnswer,
            List<Document> retrievedChunks);
}
