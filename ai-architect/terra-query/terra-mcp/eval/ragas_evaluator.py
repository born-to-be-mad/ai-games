"""RAGAS-based evaluation: Context Precision, Context Recall, Faithfulness, Answer Relevance.

Cross-provider design:
  Generator: OpenAI gpt-4o-mini  (produces answers)
  Evaluator: Anthropic claude-haiku  (scores answers — avoids self-eval bias)
"""
from __future__ import annotations

import logging
from dataclasses import dataclass

from eval.answer_generator import EvalSample
from eval.eval_config import EvalConfig, EvalThresholds

logger = logging.getLogger(__name__)

METRIC_NAMES = ("context_precision", "context_recall", "faithfulness", "answer_relevance")


@dataclass
class MetricScore:
    name: str
    score: float
    threshold: float
    min_threshold: float

    @property
    def passes(self) -> bool:
        return self.score >= self.threshold

    @property
    def above_minimum(self) -> bool:
        return self.score >= self.min_threshold

    @property
    def status(self) -> str:
        if self.passes:
            return "PASS"
        if self.above_minimum:
            return "WARN"
        return "FAIL"


@dataclass
class EvalResult:
    metric_scores: list[MetricScore]
    sample_count: int

    @property
    def all_pass(self) -> bool:
        return all(m.passes for m in self.metric_scores)

    @property
    def above_minimum(self) -> bool:
        return all(m.above_minimum for m in self.metric_scores)

    @property
    def any_hard_fail(self) -> bool:
        return any(not m.above_minimum for m in self.metric_scores)

    def to_summary(self) -> str:
        lines = [f"=== TerraQuery RAGAS Evaluation ({self.sample_count} samples) ==="]
        for m in self.metric_scores:
            lines.append(
                f"  [{m.status}] {m.name:<22} {m.score:.3f}  "
                f"(target≥{m.threshold:.2f}, min≥{m.min_threshold:.2f})"
            )
        overall = "ALL PASS" if self.all_pass else ("WARN" if self.above_minimum else "HARD FAIL")
        lines.append(f"\nOverall: {overall}")
        return "\n".join(lines)

    def to_github_summary(self) -> str:
        """Markdown table for GitHub Actions job summary."""
        rows = ["| Metric | Score | Target | Min | Status |", "|--------|-------|--------|-----|--------|"]
        for m in self.metric_scores:
            icon = "✅" if m.passes else ("⚠️" if m.above_minimum else "❌")
            rows.append(
                f"| {m.name} | {m.score:.3f} | ≥{m.threshold:.2f} | ≥{m.min_threshold:.2f} | {icon} {m.status} |"
            )
        rows.append(f"\n**Samples evaluated:** {self.sample_count}")
        return "\n".join(rows)


class RagasEvaluator:
    """Runs RAGAS evaluation on a list of EvalSamples.

    Metrics used (all scored by evaluator LLM = Anthropic claude-haiku):
      - LLMContextPrecisionWithReference: fraction of retrieved context relevant to answer
      - LLMContextRecall: fraction of ground-truth supported by retrieved context
      - Faithfulness: answer grounded in retrieved context (no hallucination)
      - ResponseRelevancy: answer addresses the question
    """

    def __init__(self, config: EvalConfig) -> None:
        self._config = config

    def evaluate(self, samples: list[EvalSample]) -> EvalResult:
        if not samples:
            raise ValueError("Cannot evaluate an empty sample list")
        ragas_samples = self._to_ragas_samples(samples)
        scores = self._run_ragas(ragas_samples)
        return self._build_result(scores, len(samples))

    def _to_ragas_samples(self, samples: list[EvalSample]):
        from ragas.dataset_schema import SingleTurnSample

        return [
            SingleTurnSample(
                user_input=s.question,
                retrieved_contexts=s.contexts if s.contexts else [""],
                response=s.answer,
                reference=s.ground_truth,
            )
            for s in samples
        ]

    def _run_ragas(self, ragas_samples) -> dict[str, float]:
        from ragas import evaluate, EvaluationDataset
        from ragas.llms import LangchainLLMWrapper
        from ragas.embeddings import LangchainEmbeddingsWrapper
        from ragas.metrics import (
            LLMContextPrecisionWithReference,
            LLMContextRecall,
            Faithfulness,
            ResponseRelevancy,
        )
        from langchain_openai import OpenAIEmbeddings
        from langchain_anthropic import ChatAnthropic

        evaluator_llm = LangchainLLMWrapper(
            ChatAnthropic(
                model=self._config.evaluator_model,
                api_key=self._config.anthropic_api_key,
                temperature=0,
            )
        )
        generator_embeddings = LangchainEmbeddingsWrapper(
            OpenAIEmbeddings(
                model="text-embedding-3-small",
                api_key=self._config.openai_api_key,
            )
        )

        metrics = [
            LLMContextPrecisionWithReference(llm=evaluator_llm),
            LLMContextRecall(llm=evaluator_llm),
            Faithfulness(llm=evaluator_llm),
            ResponseRelevancy(llm=evaluator_llm, embeddings=generator_embeddings),
        ]

        dataset = EvaluationDataset(samples=ragas_samples)
        result = evaluate(dataset=dataset, metrics=metrics)
        df = result.to_pandas()

        return {
            "context_precision": float(df["context_precision"].mean()),
            "context_recall": float(df["context_recall"].mean()),
            "faithfulness": float(df["faithfulness"].mean()),
            "answer_relevance": float(df["answer_relevancy"].mean()),
        }

    def _build_result(self, scores: dict[str, float], sample_count: int) -> EvalResult:
        t: EvalThresholds = self._config.thresholds
        metric_scores = [
            MetricScore(
                name=name,
                score=scores[name],
                threshold=t.target_for(name),
                min_threshold=t.min_for(name),
            )
            for name in METRIC_NAMES
        ]
        return EvalResult(metric_scores=metric_scores, sample_count=sample_count)
