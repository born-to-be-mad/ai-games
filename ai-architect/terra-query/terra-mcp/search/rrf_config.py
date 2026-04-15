"""RRFConfig: Reciprocal Rank Fusion parameters, A/B testable via env vars."""

import os
from dataclasses import dataclass


@dataclass
class RRFConfig:
    """
    Reciprocal Rank Fusion parameters.

    k: smoothing constant (higher = more weight to lower-ranked docs).
       Default k=60 from Cormack et al. original paper.
    bm25_weight / vector_weight: relative contribution of each retriever.
    top_k_per_retriever: candidates from each index before fusion.
    final_top_k: results returned after fusion.
    """

    k: int = 60
    bm25_weight: float = 1.0
    vector_weight: float = 1.0
    top_k_per_retriever: int = 20
    final_top_k: int = 10

    @classmethod
    def from_env(cls) -> "RRFConfig":
        """Load config from environment variables for A/B testing without code changes."""
        return cls(
            k=int(os.getenv("RRF_K", "60")),
            bm25_weight=float(os.getenv("RRF_BM25_WEIGHT", "1.0")),
            vector_weight=float(os.getenv("RRF_VECTOR_WEIGHT", "1.0")),
            top_k_per_retriever=int(os.getenv("RRF_TOP_K_PER_RETRIEVER", "20")),
            final_top_k=int(os.getenv("RRF_FINAL_TOP_K", "10")),
        )
