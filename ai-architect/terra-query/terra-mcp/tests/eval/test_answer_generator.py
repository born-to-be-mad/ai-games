"""Unit tests for AnswerGenerator and McpDirectRetriever."""
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from eval.answer_generator import AnswerGenerator, EvalSample, McpDirectRetriever, _row_to_context


class TestMcpDirectRetriever:
    @pytest.fixture()
    def engine_with_results(self):
        engine = MagicMock()
        engine.search.return_value = [
            {"record_id": "eosdis_1", "score": 0.90, "text": "Earthquake in Indonesia 2004"},
            {"record_id": "eosdis_3", "score": 0.75, "text": "Earthquake in Haiti 2010"},
        ]
        return engine

    @pytest.fixture()
    def small_repo(self, sample_df):
        repo = MagicMock()
        repo.df = sample_df
        return repo

    def test_retrieve_returns_context_strings(self, small_repo, engine_with_results):
        retriever = McpDirectRetriever(small_repo, engine_with_results, top_k=2)
        contexts = retriever.retrieve("earthquake deaths Asia")
        assert len(contexts) == 2
        assert all(isinstance(c, str) for c in contexts)
        assert all(len(c) > 10 for c in contexts)

    def test_retrieve_no_engine_returns_empty(self, small_repo):
        retriever = McpDirectRetriever(small_repo, None, top_k=5)
        contexts = retriever.retrieve("any query")
        assert contexts == []

    def test_retrieve_respects_top_k(self, small_repo, engine_with_results):
        engine_with_results.search.return_value = [
            {"record_id": f"rec_{i}", "score": 0.9 - i * 0.1, "text": f"Event {i}"}
            for i in range(10)
        ]
        retriever = McpDirectRetriever(small_repo, engine_with_results, top_k=3)
        contexts = retriever.retrieve("floods")
        assert len(contexts) <= 3

    def test_retrieve_falls_back_to_text_when_no_row_match(self, small_repo, engine_with_results):
        engine_with_results.search.return_value = [
            {"record_id": "nonexistent_999", "score": 0.80, "text": "Some fallback text"},
        ]
        retriever = McpDirectRetriever(small_repo, engine_with_results, top_k=5)
        contexts = retriever.retrieve("storms")
        assert any("fallback" in c or "Some" in c for c in contexts)

    def test_retrieve_context_contains_country(self, small_repo, engine_with_results):
        retriever = McpDirectRetriever(small_repo, engine_with_results, top_k=2)
        contexts = retriever.retrieve("earthquake")
        assert any("Indonesia" in c or "Haiti" in c for c in contexts)


class TestRowToContext:
    def test_basic_row(self):
        row = {
            "disaster_type": "flood",
            "country": "Bangladesh",
            "region": "Southern Asia",
            "start_date": pd.Timestamp("1998-07-01"),
            "deaths": 1050.0,
            "economic_damage_usd": 5_900_000_000.0,
            "affected": 30_000_000.0,
            "magnitude": None,
        }
        text = _row_to_context(row)
        assert "Flood" in text
        assert "Bangladesh" in text
        assert "1998" in text
        assert "1050" in text or "deaths" in text

    def test_na_fields_show_unknown(self):
        row = {
            "disaster_type": "drought",
            "country": "Zimbabwe",
            "region": "Eastern Africa",
            "start_date": pd.Timestamp("2020-01-01"),
            "deaths": pd.NA,
            "economic_damage_usd": pd.NA,
            "affected": 5_700_000.0,
            "magnitude": None,
        }
        text = _row_to_context(row)
        assert "unknown" in text.lower()
        assert "Zimbabwe" in text


class TestAnswerGenerator:
    @pytest.fixture()
    def mock_retriever(self):
        retriever = MagicMock()
        retriever.retrieve.return_value = [
            "Flood in Bangladesh 1998. Deaths: 1050. Affected: 30,000,000.",
            "Bangladesh flood frequency doubled in 2010s vs 1990s.",
        ]
        return retriever

    @pytest.fixture()
    def mock_generator_fn(self):
        return lambda prompt: "Bangladesh has experienced many flood disasters historically."

    def test_generate_sample_returns_eval_sample(self, mock_retriever, mock_generator_fn):
        gen = AnswerGenerator(mock_retriever, mock_generator_fn)
        sample = gen.generate_sample("gd-001", "How many floods in Bangladesh?", "Over 100 floods.")
        assert isinstance(sample, EvalSample)
        assert sample.question_id == "gd-001"
        assert sample.question == "How many floods in Bangladesh?"
        assert sample.ground_truth == "Over 100 floods."
        assert len(sample.contexts) == 2
        assert "Bangladesh" in sample.answer

    def test_generate_sample_empty_contexts_returns_fallback_answer(self, mock_generator_fn):
        retriever = MagicMock()
        retriever.retrieve.return_value = []
        gen = AnswerGenerator(retriever, mock_generator_fn)
        sample = gen.generate_sample("gd-001", "What happened?", "Something happened.")
        assert "Insufficient" in sample.answer or sample.answer != ""

    def test_generate_sample_prompt_includes_question(self, mock_generator_fn):
        captured = []

        def capture_fn(prompt: str) -> str:
            captured.append(prompt)
            return "answer"

        retriever = MagicMock()
        retriever.retrieve.return_value = ["some context"]
        gen = AnswerGenerator(retriever, capture_fn)
        gen.generate_sample("gd-001", "How many floods?", "ground truth")
        assert len(captured) == 1
        assert "How many floods?" in captured[0]
        assert "some context" in captured[0]

    def test_generate_batch_processes_all_entries(self, mock_retriever, mock_generator_fn):
        gen = AnswerGenerator(mock_retriever, mock_generator_fn)
        entries = [
            {"id": f"gd-{i:03d}", "question": f"Q{i}", "ground_truth": f"GT{i}"}
            for i in range(1, 4)
        ]
        samples = gen.generate_batch(entries)
        assert len(samples) == 3
        assert [s.question_id for s in samples] == ["gd-001", "gd-002", "gd-003"]

    def test_generate_batch_skips_failing_entries(self, mock_generator_fn):
        call_count = 0

        def failing_retriever_retrieve(question):
            nonlocal call_count
            call_count += 1
            if call_count == 2:
                raise RuntimeError("Simulated retrieval error")
            return ["context"]

        retriever = MagicMock()
        retriever.retrieve.side_effect = failing_retriever_retrieve
        gen = AnswerGenerator(retriever, mock_generator_fn)
        entries = [
            {"id": "gd-001", "question": "Q1", "ground_truth": "GT1"},
            {"id": "gd-002", "question": "Q2", "ground_truth": "GT2"},
            {"id": "gd-003", "question": "Q3", "ground_truth": "GT3"},
        ]
        samples = gen.generate_batch(entries)
        assert len(samples) == 2  # gd-002 skipped
