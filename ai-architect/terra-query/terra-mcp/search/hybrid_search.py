"""HybridSearchEngine: BM25 + FAISS + Reciprocal Rank Fusion."""
from __future__ import annotations

import logging
from typing import Any

import numpy as np

from search.rrf_config import RRFConfig

logger = logging.getLogger(__name__)


class HybridSearchEngine:
    """
    Hybrid retrieval: BM25 keyword + dense FAISS + weighted Reciprocal Rank Fusion.

    RRF score formula (Cormack et al.):
        score(d) = Σ weight_i / (k + rank_i(d))
    """

    def __init__(
        self,
        indices: Any,          # SearchIndices from index_builder
        config: RRFConfig | None = None,
    ) -> None:
        self._indices = indices
        self._config = config or RRFConfig.from_env()

    def search(self, query: str) -> list[dict[str, Any]]:
        """
        Search using hybrid BM25 + FAISS with RRF fusion.

        Returns list of dicts with keys: record_id, score, text.
        """
        if not query.strip():
            return []

        cfg = self._config
        bm25_results = self._bm25_search(query, cfg.top_k_per_retriever)
        faiss_results = self._faiss_search(query, cfg.top_k_per_retriever)

        # If only one retriever available, return its results directly
        if not bm25_results and not faiss_results:
            return []

        enriched = self._indices.enriched_texts if self._indices else []
        record_ids = self._indices.record_ids if self._indices else []

        if not bm25_results:
            return [
                {
                    "record_id": record_ids[i] if i < len(record_ids) else str(i),
                    "score": score,
                    "text": enriched[i] if i < len(enriched) else "",
                }
                for i, score in faiss_results[: cfg.final_top_k]
            ]
        if not faiss_results:
            return [
                {
                    "record_id": record_ids[i] if i < len(record_ids) else str(i),
                    "score": score,
                    "text": enriched[i] if i < len(enriched) else "",
                }
                for i, score in bm25_results[: cfg.final_top_k]
            ]

        # Weighted RRF fusion
        rrf_scores: dict[int, float] = {}

        for rank, (idx, _) in enumerate(bm25_results):
            rrf_scores[idx] = rrf_scores.get(idx, 0.0) + cfg.bm25_weight / (cfg.k + rank + 1)

        for rank, (idx, _) in enumerate(faiss_results):
            rrf_scores[idx] = rrf_scores.get(idx, 0.0) + cfg.vector_weight / (cfg.k + rank + 1)

        sorted_ids = sorted(rrf_scores, key=rrf_scores.__getitem__, reverse=True)
        top_ids = sorted_ids[: cfg.final_top_k]

        return [
            {
                "record_id": record_ids[i] if i < len(record_ids) else str(i),
                "score": rrf_scores[i],
                "text": enriched[i] if i < len(enriched) else "",
            }
            for i in top_ids
        ]

    # -------------------------------------------------------------------------
    # private
    # -------------------------------------------------------------------------

    def _bm25_search(self, query: str, top_k: int) -> list[tuple[int, float]]:
        bm25 = getattr(self._indices, "bm25", None)
        if bm25 is None:
            return []
        try:
            tokenized = query.lower().split()
            scores = bm25.get_scores(tokenized)
            top_indices = np.argsort(scores)[::-1][:top_k]
            return [(int(i), float(scores[i])) for i in top_indices if scores[i] > 0]
        except Exception as exc:
            logger.warning("BM25 search error: %s", exc)
            return []

    def _faiss_search(self, query: str, top_k: int) -> list[tuple[int, float]]:
        faiss_index = getattr(self._indices, "faiss_index", None)
        if faiss_index is None:
            return []
        try:
            import os
            model_name = os.getenv("EMBEDDING_MODEL", "BAAI/bge-base-en-v1.5")
            model = _get_cached_model(model_name)
            embedding = model.encode([query], normalize_embeddings=True)
            embedding = np.array(embedding, dtype=np.float32)
            scores, indices = faiss_index.search(embedding, top_k)
            return [
                (int(idx), float(score))
                for idx, score in zip(indices[0], scores[0])
                if idx >= 0 and score > 0
            ]
        except Exception as exc:
            logger.warning("FAISS search error: %s", exc)
            return []


# Module-level cache for the embedding model (avoid reloading per search)
_model_cache: dict[str, Any] = {}


def _get_cached_model(name: str) -> Any:
    if name not in _model_cache:
        from sentence_transformers import SentenceTransformer
        _model_cache[name] = SentenceTransformer(name)
    return _model_cache[name]
