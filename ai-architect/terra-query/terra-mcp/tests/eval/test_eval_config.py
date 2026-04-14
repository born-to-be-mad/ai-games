"""Unit tests for EvalConfig and EvalThresholds."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from eval.eval_config import EvalConfig, EvalThresholds


class TestEvalThresholds:
    def test_defaults(self):
        t = EvalThresholds()
        assert t.context_precision == 0.80
        assert t.context_recall == 0.75
        assert t.faithfulness == 0.85
        assert t.answer_relevance == 0.80

    def test_min_thresholds_are_ten_below_target(self):
        t = EvalThresholds()
        assert t.context_precision_min == pytest.approx(0.70)
        assert t.context_recall_min == pytest.approx(0.65)
        assert t.faithfulness_min == pytest.approx(0.75)
        assert t.answer_relevance_min == pytest.approx(0.70)

    def test_target_for_helper(self):
        t = EvalThresholds()
        assert t.target_for("context_precision") == 0.80
        assert t.target_for("faithfulness") == 0.85

    def test_min_for_helper(self):
        t = EvalThresholds()
        assert t.min_for("context_recall") == pytest.approx(0.65)
        assert t.min_for("answer_relevance") == pytest.approx(0.70)

    def test_custom_thresholds(self):
        t = EvalThresholds(context_precision=0.90, faithfulness=0.95)
        assert t.context_precision == 0.90
        assert t.context_precision_min == pytest.approx(0.80)
        assert t.faithfulness_min == pytest.approx(0.85)

    def test_frozen_immutability(self):
        t = EvalThresholds()
        with pytest.raises((AttributeError, TypeError)):
            t.context_precision = 0.99  # type: ignore[misc]


class TestEvalConfig:
    def test_defaults(self):
        cfg = EvalConfig(openai_api_key="sk-test", anthropic_api_key="ant-test")
        assert cfg.generator_model == "gpt-4o-mini"
        assert cfg.evaluator_model == "claude-haiku-4-5-20251001"
        assert cfg.canary_size == 10
        assert cfg.full_size == 30
        assert cfg.top_k_contexts == 5

    def test_from_env_reads_api_keys(self, monkeypatch):
        monkeypatch.setenv("OPENAI_API_KEY", "sk-env-key")
        monkeypatch.setenv("ANTHROPIC_API_KEY", "ant-env-key")
        cfg = EvalConfig.from_env()
        assert cfg.openai_api_key == "sk-env-key"
        assert cfg.anthropic_api_key == "ant-env-key"

    def test_from_env_missing_keys_returns_empty_strings(self, monkeypatch):
        monkeypatch.delenv("OPENAI_API_KEY", raising=False)
        monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
        cfg = EvalConfig.from_env()
        assert cfg.openai_api_key == ""
        assert cfg.anthropic_api_key == ""

    def test_validate_passes_with_keys(self):
        cfg = EvalConfig(openai_api_key="sk-test", anthropic_api_key="ant-test")
        cfg.validate()  # should not raise

    def test_validate_raises_on_missing_openai_key(self):
        cfg = EvalConfig(openai_api_key="", anthropic_api_key="ant-test")
        with pytest.raises(ValueError, match="OPENAI_API_KEY"):
            cfg.validate()

    def test_validate_raises_on_missing_anthropic_key(self):
        cfg = EvalConfig(openai_api_key="sk-test", anthropic_api_key="")
        with pytest.raises(ValueError, match="ANTHROPIC_API_KEY"):
            cfg.validate()

    def test_threshold_defaults_propagated(self):
        cfg = EvalConfig()
        assert cfg.thresholds.faithfulness == 0.85
