"""Unit tests for RagasEvaluator, MetricScore, and EvalResult."""

import sys
from pathlib import Path
from unittest.mock import patch

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from eval.answer_generator import EvalSample
from eval.eval_config import EvalConfig
from eval.ragas_evaluator import EvalResult, MetricScore, RagasEvaluator


def _make_sample(qid: str = "gd-001") -> EvalSample:
    return EvalSample(
        question_id=qid,
        question="How many flood events occurred in Bangladesh?",
        ground_truth="Over 100 floods between 1990 and 2021.",
        contexts=[
            "Bangladesh flood 1998: 1050 deaths.",
            "Flood frequency doubled in 2010s.",
        ],
        answer="Bangladesh experienced over 100 flood events between 1990 and 2021.",
    )


def _make_config(**overrides) -> EvalConfig:
    defaults = dict(openai_api_key="sk-test", anthropic_api_key="ant-test")
    defaults.update(overrides)
    return EvalConfig(**defaults)


class TestMetricScore:
    def test_passes_when_score_above_threshold(self):
        m = MetricScore("faithfulness", score=0.90, threshold=0.85, min_threshold=0.75)
        assert m.passes is True

    def test_fails_when_score_below_threshold(self):
        m = MetricScore("faithfulness", score=0.80, threshold=0.85, min_threshold=0.75)
        assert m.passes is False

    def test_above_minimum_when_between_min_and_target(self):
        m = MetricScore("faithfulness", score=0.80, threshold=0.85, min_threshold=0.75)
        assert m.above_minimum is True
        assert m.status == "WARN"

    def test_hard_fail_when_below_minimum(self):
        m = MetricScore("faithfulness", score=0.70, threshold=0.85, min_threshold=0.75)
        assert m.above_minimum is False
        assert m.status == "FAIL"

    def test_pass_status(self):
        m = MetricScore(
            "context_precision", score=0.85, threshold=0.80, min_threshold=0.70
        )
        assert m.status == "PASS"


class TestEvalResult:
    def _pass_scores(self) -> list[MetricScore]:
        return [
            MetricScore("context_precision", 0.85, 0.80, 0.70),
            MetricScore("context_recall", 0.80, 0.75, 0.65),
            MetricScore("faithfulness", 0.90, 0.85, 0.75),
            MetricScore("answer_relevance", 0.85, 0.80, 0.70),
        ]

    def test_all_pass_when_all_above_target(self):
        result = EvalResult(self._pass_scores(), sample_count=10)
        assert result.all_pass is True

    def test_any_hard_fail_false_when_all_above_min(self):
        scores = [MetricScore("faithfulness", 0.78, 0.85, 0.75)]  # warn only
        result = EvalResult(scores, sample_count=5)
        assert result.any_hard_fail is False

    def test_any_hard_fail_true_when_below_min(self):
        scores = [MetricScore("faithfulness", 0.60, 0.85, 0.75)]  # hard fail
        result = EvalResult(scores, sample_count=5)
        assert result.any_hard_fail is True

    def test_above_minimum_false_when_one_hard_fail(self):
        scores = self._pass_scores()
        scores[0] = MetricScore("context_precision", 0.60, 0.80, 0.70)
        result = EvalResult(scores, sample_count=10)
        assert result.above_minimum is False

    def test_to_summary_contains_all_metric_names(self):
        result = EvalResult(self._pass_scores(), sample_count=10)
        summary = result.to_summary()
        assert "context_precision" in summary
        assert "context_recall" in summary
        assert "faithfulness" in summary
        assert "answer_relevance" in summary

    def test_to_summary_shows_overall_pass(self):
        result = EvalResult(self._pass_scores(), sample_count=10)
        assert "ALL PASS" in result.to_summary()

    def test_to_summary_shows_hard_fail(self):
        scores = [MetricScore("faithfulness", 0.50, 0.85, 0.75)]
        result = EvalResult(scores, sample_count=5)
        assert "HARD FAIL" in result.to_summary()

    def test_to_github_summary_is_markdown_table(self):
        result = EvalResult(self._pass_scores(), sample_count=10)
        md = result.to_github_summary()
        assert "| Metric |" in md
        assert "context_precision" in md
        assert "✅" in md

    def test_sample_count_in_summary(self):
        result = EvalResult(self._pass_scores(), sample_count=30)
        assert "30" in result.to_summary()


class TestRagasEvaluator:
    def test_evaluate_empty_samples_raises(self):
        cfg = _make_config()
        evaluator = RagasEvaluator(cfg)
        with pytest.raises(ValueError, match="empty"):
            evaluator.evaluate([])

    def test_build_result_applies_thresholds(self):
        cfg = _make_config()
        evaluator = RagasEvaluator(cfg)
        scores = {
            "context_precision": 0.82,
            "context_recall": 0.77,
            "faithfulness": 0.88,
            "answer_relevance": 0.83,
        }
        result = evaluator._build_result(scores, sample_count=10)
        assert result.sample_count == 10
        assert len(result.metric_scores) == 4
        assert result.all_pass is True

    def test_build_result_hard_fail_when_below_min(self):
        cfg = _make_config()
        evaluator = RagasEvaluator(cfg)
        scores = {
            "context_precision": 0.60,  # below min=0.70
            "context_recall": 0.77,
            "faithfulness": 0.88,
            "answer_relevance": 0.83,
        }
        result = evaluator._build_result(scores, sample_count=5)
        assert result.any_hard_fail is True
        assert not result.above_minimum

    @patch("eval.ragas_evaluator.RagasEvaluator._run_ragas")
    @patch("eval.ragas_evaluator.RagasEvaluator._to_ragas_samples")
    def test_evaluate_calls_run_ragas_and_builds_result(
        self, mock_to_samples, mock_run_ragas
    ):
        mock_to_samples.return_value = ["stub_sample_1", "stub_sample_2"]
        mock_run_ragas.return_value = {
            "context_precision": 0.83,
            "context_recall": 0.78,
            "faithfulness": 0.91,
            "answer_relevance": 0.85,
        }
        cfg = _make_config()
        evaluator = RagasEvaluator(cfg)
        samples = [_make_sample("gd-001"), _make_sample("gd-002")]
        result = evaluator.evaluate(samples)
        mock_to_samples.assert_called_once_with(samples)
        mock_run_ragas.assert_called_once()
        assert result.sample_count == 2
        assert result.all_pass is True

    def test_to_ragas_samples_maps_fields_correctly(self):
        # Mock ragas.dataset_schema so test runs without ragas installed
        import types

        fake_ragas = types.ModuleType("ragas")
        fake_schema = types.ModuleType("ragas.dataset_schema")

        class FakeSingleTurnSample:
            def __init__(self, **kwargs):
                for k, v in kwargs.items():
                    setattr(self, k, v)

        fake_schema.SingleTurnSample = FakeSingleTurnSample
        fake_ragas.dataset_schema = fake_schema

        with patch.dict(
            "sys.modules", {"ragas": fake_ragas, "ragas.dataset_schema": fake_schema}
        ):
            cfg = _make_config()
            evaluator = RagasEvaluator(cfg)
            samples = [_make_sample()]
            ragas_samples = evaluator._to_ragas_samples(samples)

        assert len(ragas_samples) == 1
        s = ragas_samples[0]
        assert s.user_input == "How many flood events occurred in Bangladesh?"
        assert s.reference == "Over 100 floods between 1990 and 2021."
        assert len(s.retrieved_contexts) == 2

    def test_to_ragas_samples_empty_contexts_padded(self):
        import types

        fake_ragas = types.ModuleType("ragas")
        fake_schema = types.ModuleType("ragas.dataset_schema")

        class FakeSingleTurnSample:
            def __init__(self, **kwargs):
                for k, v in kwargs.items():
                    setattr(self, k, v)

        fake_schema.SingleTurnSample = FakeSingleTurnSample
        fake_ragas.dataset_schema = fake_schema

        with patch.dict(
            "sys.modules", {"ragas": fake_ragas, "ragas.dataset_schema": fake_schema}
        ):
            cfg = _make_config()
            evaluator = RagasEvaluator(cfg)
            sample = EvalSample("gd-001", "Q?", "GT", contexts=[], answer="A")
            ragas_samples = evaluator._to_ragas_samples([sample])

        assert ragas_samples[0].retrieved_contexts == [""]
