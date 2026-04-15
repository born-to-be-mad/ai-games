"""IndexCache: disk-persisted FAISS + BM25 indices with MD5 hash-based invalidation."""

import hashlib
import logging
import os
import pickle
from pathlib import Path
from typing import Any, Callable

logger = logging.getLogger(__name__)

_DEFAULT_CACHE_DIR = Path(os.getenv("INDEX_CACHE_DIR", "/data/indices"))


class IndexCache:
    """
    Load or build search indices.
    - If data files haven't changed (same MD5), loads cached indices in ~2s.
    - On first run or data change, rebuilds (~3–5 min for 1M records) and caches.
    """

    def __init__(self, cache_dir: Path = _DEFAULT_CACHE_DIR) -> None:
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def get_or_build(self, data_hash: str, builder_fn: Callable[[], Any]) -> Any:
        """
        Return cached indices if data_hash matches; otherwise call builder_fn and cache result.

        Args:
            data_hash: MD5 hex of combined data files.
            builder_fn: Zero-arg callable that returns the indices object (FAISS + BM25).
        """
        cache_path = self.cache_dir / f"index_{data_hash}.pkl"

        if cache_path.exists():
            logger.info("Loading cached indices from %s", cache_path)
            try:
                return pickle.loads(cache_path.read_bytes())
            except Exception as exc:
                logger.warning("Cache load failed (%s) — rebuilding", exc)

        logger.info("Building indices from scratch (first run or data changed)...")
        indices = builder_fn()
        try:
            cache_path.write_bytes(pickle.dumps(indices))
            logger.info("Indices cached to %s", cache_path)
        except Exception as exc:
            logger.warning("Failed to write index cache: %s", exc)

        return indices

    def invalidate(self) -> None:
        """Remove all cached index files."""
        for f in self.cache_dir.glob("index_*.pkl"):
            f.unlink(missing_ok=True)
        logger.info("Index cache cleared")

    @staticmethod
    def compute_data_hash(*file_paths: Path) -> str:
        """Compute MD5 of concatenated file contents. Sorted for determinism."""
        h = hashlib.md5()
        for p in sorted(file_paths):
            if Path(p).exists():
                h.update(Path(p).read_bytes())
        return h.hexdigest()
