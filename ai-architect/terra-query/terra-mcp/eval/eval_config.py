"""Evaluation configuration: thresholds, provider selection, dataset sizing."""
from __future__ import annotations

import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class EvalThresholds:
    """Pass/fail thresholds for RAGAS metrics.

    Pass threshold: target score — regression alert if any metric drops below.
    Min threshold: hard-fail floor (pass_threshold - 0.10) — CI blocks on breach.
    """

    context_precision: float = 0.80
    context_recall: float = 0.75
    faithfulness: float = 0.85
    answer_relevance: float = 0.80

    @property
    def context_precision_min(self) -> float:
        return self.context_precision - 0.10

    @property
    def context_recall_min(self) -> float:
        return self.context_recall - 0.10

    @property
    def faithfulness_min(self) -> float:
        return self.faithfulness - 0.10

    @property
    def answer_relevance_min(self) -> float:
        return self.answer_relevance - 0.10

    def min_for(self, metric: str) -> float:
        return getattr(self, f"{metric}_min")

    def target_for(self, metric: str) -> float:
        return getattr(self, metric)


@dataclass
class EvalConfig:
    """Cross-provider evaluation config (generator ≠ evaluator to eliminate self-eval bias).

    Generator: OpenAI gpt-4o-mini — produces answers from retrieved contexts.
    Evaluator: Anthropic claude-haiku — scores those answers via RAGAS metrics.
    """

    generator_model: str = "gpt-4o-mini"
    evaluator_model: str = "claude-haiku-4-5-20251001"
    openai_api_key: str = field(default_factory=lambda: os.environ.get("OPENAI_API_KEY", ""))
    anthropic_api_key: str = field(default_factory=lambda: os.environ.get("ANTHROPIC_API_KEY", ""))
    thresholds: EvalThresholds = field(default_factory=EvalThresholds)
    canary_size: int = 10
    full_size: int = 30
    top_k_contexts: int = 5

    @classmethod
    def from_env(cls) -> "EvalConfig":
        return cls()

    def validate(self) -> None:
        """Raise ValueError if required API keys are missing."""
        if not self.openai_api_key:
            raise ValueError("OPENAI_API_KEY not set — required for generator (gpt-4o-mini)")
        if not self.anthropic_api_key:
            raise ValueError("ANTHROPIC_API_KEY not set — required for evaluator (claude-haiku)")
