"""Builds FAISS + BM25 search indices from disaster records."""

import logging
import os
import time
from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-base-en-v1.5")


@dataclass
class SearchIndices:
    """Container for both BM25 and FAISS indices, plus the enriched texts."""

    bm25: Any  # rank_bm25.BM25Okapi
    faiss_index: Any  # faiss.IndexFlatIP (inner product for cosine on normalized vecs)
    enriched_texts: list[str]
    record_ids: list[str]


def build_indices(df: pd.DataFrame, chunker: Any) -> SearchIndices:
    """
    Build BM25 and FAISS indices from disaster DataFrame.

    Args:
        df: Normalized disaster DataFrame.
        chunker: HierarchicalChunker instance for text enrichment.

    Returns:
        SearchIndices with bm25, faiss_index, enriched_texts, record_ids.
    """
    if df.empty:
        logger.warning("Empty DataFrame — building minimal indices")
        return _build_empty_indices()

    logger.info("Building search indices for %d records...", len(df))

    # Enrich records → natural language text for embedding
    enriched_texts: list[str] = []
    record_ids: list[str] = []

    for _, row in df.iterrows():
        text = chunker.enrich_text(row.to_dict())
        enriched_texts.append(text)
        record_ids.append(str(row.get("event_id", len(record_ids))))

    # BM25 index
    bm25 = _build_bm25(enriched_texts)

    # FAISS index
    faiss_index = _build_faiss(enriched_texts)

    logger.info("Indices built: %d documents", len(enriched_texts))
    return SearchIndices(
        bm25=bm25,
        faiss_index=faiss_index,
        enriched_texts=enriched_texts,
        record_ids=record_ids,
    )


def _build_bm25(texts: list[str]) -> Any:
    try:
        from rank_bm25 import BM25Okapi
    except ImportError:
        logger.warning("rank-bm25 not installed — BM25 disabled")
        return None
    tokenized = [t.lower().split() for t in texts]
    return BM25Okapi(tokenized)


def _build_faiss(texts: list[str]) -> Any:
    try:
        import faiss
        from sentence_transformers import SentenceTransformer
    except ImportError:
        logger.warning(
            "faiss-cpu or sentence-transformers not installed — FAISS disabled"
        )
        return None

    total = len(texts)
    batch_size = int(os.getenv("EMBEDDING_BATCH_SIZE", "16"))
    logger.info(
        "Embedding %d texts with %s (batch_size=%d)...",
        total,
        EMBEDDING_MODEL,
        batch_size,
    )
    model = SentenceTransformer(EMBEDDING_MODEL)

    started = time.perf_counter()
    index: Any = None
    log_every = max(batch_size * 50, 5000)
    for start in range(0, total, batch_size):
        end = min(start + batch_size, total)
        chunk = texts[start:end]
        chunk_embeddings = model.encode(
            chunk,
            batch_size=batch_size,
            show_progress_bar=False,
            normalize_embeddings=True,  # needed for cosine via inner product
        )
        chunk_array = np.array(chunk_embeddings, dtype=np.float32)
        if index is None:
            dim = chunk_array.shape[1]
            index = faiss.IndexFlatIP(
                dim
            )  # inner product on normalized vectors = cosine
        index.add(chunk_array)

        processed = end
        if processed == total or processed % log_every == 0:
            elapsed = max(time.perf_counter() - started, 1e-6)
            rate = processed / elapsed
            remaining = total - processed
            eta = remaining / rate if rate > 0 else 0.0
            logger.info(
                "Embedding progress: %d/%d (%.1f%%), %.1f texts/s, eta %.1fs",
                processed,
                total,
                (processed / total) * 100.0,
                rate,
                eta,
            )

    logger.info("Embedding completed in %.2fs", time.perf_counter() - started)
    return index


def _build_empty_indices() -> SearchIndices:
    return SearchIndices(
        bm25=None,
        faiss_index=None,
        enriched_texts=[],
        record_ids=[],
    )
