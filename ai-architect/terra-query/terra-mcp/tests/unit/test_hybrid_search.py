"""Unit tests for HybridSearchEngine and RRFConfig."""

from search.rrf_config import RRFConfig


class TestRRFConfig:
    def test_defaults(self):
        cfg = RRFConfig()
        assert cfg.k == 60
        assert cfg.bm25_weight == 1.0
        assert cfg.vector_weight == 1.0
        assert cfg.top_k_per_retriever == 20
        assert cfg.final_top_k == 10

    def test_from_env_defaults(self, monkeypatch):
        # No env vars set — should use defaults
        for var in ("RRF_K", "RRF_BM25_WEIGHT", "RRF_VECTOR_WEIGHT"):
            monkeypatch.delenv(var, raising=False)
        cfg = RRFConfig.from_env()
        assert cfg.k == 60

    def test_from_env_overrides(self, monkeypatch):
        monkeypatch.setenv("RRF_K", "30")
        monkeypatch.setenv("RRF_BM25_WEIGHT", "0.5")
        monkeypatch.setenv("RRF_VECTOR_WEIGHT", "2.0")
        cfg = RRFConfig.from_env()
        assert cfg.k == 30
        assert cfg.bm25_weight == 0.5
        assert cfg.vector_weight == 2.0


class TestHybridSearchEngine:
    def test_empty_query_returns_empty(self):
        from unittest.mock import MagicMock
        from search.hybrid_search import HybridSearchEngine

        engine = HybridSearchEngine(
            indices=MagicMock(
                bm25=None, faiss_index=None, enriched_texts=[], record_ids=[]
            )
        )
        assert engine.search("") == []

    def test_no_indices_returns_empty(self):
        from unittest.mock import MagicMock
        from search.hybrid_search import HybridSearchEngine

        indices = MagicMock()
        indices.bm25 = None
        indices.faiss_index = None
        indices.enriched_texts = []
        indices.record_ids = []
        engine = HybridSearchEngine(indices=indices)
        result = engine.search("flood Bangladesh")
        assert result == []

    def test_rrf_scores_sum_correctly(self):
        """RRF score formula: weight / (k + rank + 1) — verify with bm25 only."""
        from unittest.mock import MagicMock
        import numpy as np
        from search.hybrid_search import HybridSearchEngine

        mock_indices = MagicMock()
        mock_indices.enriched_texts = ["text0", "text1", "text2"]
        mock_indices.record_ids = ["r0", "r1", "r2"]

        mock_bm25 = MagicMock()
        mock_bm25.get_scores.return_value = np.array([0.9, 0.5, 0.1])
        mock_indices.bm25 = mock_bm25
        mock_indices.faiss_index = None

        engine = HybridSearchEngine(
            indices=mock_indices,
            config=RRFConfig(k=60, bm25_weight=1.0, vector_weight=1.0, final_top_k=3),
        )
        results = engine.search("some query")
        # Top result should have highest BM25 score (index 0)
        assert results[0]["record_id"] == "r0"
