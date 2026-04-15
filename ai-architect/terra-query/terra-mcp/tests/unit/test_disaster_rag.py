"""Unit tests for search_disasters_semantic tool logic."""

from unittest.mock import MagicMock

import pytest

from tools.disaster_rag import search_disasters_semantic_logic


@pytest.fixture()
def mock_engine():
    engine = MagicMock()
    engine.search.return_value = [
        {
            "record_id": "eosdis_1",
            "score": 0.85,
            "text": "An earthquake in Indonesia in 2004.",
        },
        {
            "record_id": "eosdis_3",
            "score": 0.72,
            "text": "An earthquake in Haiti in 2010.",
        },
    ]
    return engine


class TestSearchDisastersSemantic:
    def test_returns_results(self, mock_repo, mock_engine):
        result = search_disasters_semantic_logic(
            mock_repo, mock_engine, "devastating tsunami"
        )
        assert "1." in result
        assert "score=" in result

    def test_empty_query_returns_error(self, mock_repo, mock_engine):
        result = search_disasters_semantic_logic(mock_repo, mock_engine, "")
        assert "empty" in result.lower() or "provide" in result.lower()

    def test_whitespace_query_returns_error(self, mock_repo, mock_engine):
        result = search_disasters_semantic_logic(mock_repo, mock_engine, "   ")
        assert "empty" in result.lower() or "provide" in result.lower()

    def test_no_engine_graceful(self, mock_repo):
        result = search_disasters_semantic_logic(mock_repo, None, "flood")
        assert "not available" in result.lower()

    def test_no_results_graceful(self, mock_repo, mock_engine):
        mock_engine.search.return_value = []
        result = search_disasters_semantic_logic(
            mock_repo, mock_engine, "ancient roman flood"
        )
        assert "no matching" in result.lower()

    def test_result_count_header(self, mock_repo, mock_engine):
        result = search_disasters_semantic_logic(mock_repo, mock_engine, "tsunami")
        assert "Top 2" in result or "2 semantically" in result

    def test_record_lookup_shows_country(self, mock_repo, mock_engine):
        result = search_disasters_semantic_logic(mock_repo, mock_engine, "earthquake")
        assert "Indonesia" in result or "Haiti" in result
