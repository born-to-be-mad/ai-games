"""
Memory profiler for terra-mcp startup and search operations.
Uses tracemalloc + psutil to measure peak RSS and allocation.
Results drive Docker deploy.resources.limits.

Usage:
    python scripts/memory_profile.py [--data-dir /path/to/data]
"""
import argparse
import logging
import os
import sys
import time
import tracemalloc
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

sys.path.insert(0, str(Path(__file__).parent.parent))


def _get_rss_mb() -> float:
    """Current process RSS in MB."""
    try:
        import psutil
        return psutil.Process(os.getpid()).memory_info().rss / (1024 * 1024)
    except ImportError:
        return 0.0


def profile_startup(data_dir: Path) -> dict:
    """Profile the full startup sequence: load → deduplicate → build indices."""
    os.environ["DATA_DIR"] = str(data_dir)

    logger.info("=== Starting memory profile ===")
    rss_before = _get_rss_mb()

    tracemalloc.start()
    t0 = time.perf_counter()

    from data.loaders.eosdis_loader import EosdisLoader
    from data.loaders.noaa_loader import NoaaLoader
    from data.repository import DisasterRepository
    from data.index.index_builder import build_indices
    from data.index.index_cache import IndexCache
    from search.hierarchical_chunker import HierarchicalChunker
    from search.hybrid_search import HybridSearchEngine

    repo = DisasterRepository(loaders=[EosdisLoader(data_dir), NoaaLoader(data_dir)])
    repo.load()
    t_load = time.perf_counter()

    chunker = HierarchicalChunker()
    cache = IndexCache(cache_dir=Path("/tmp/terra-query-profile-cache"))
    data_files = repo.get_data_file_paths()
    data_hash = IndexCache.compute_data_hash(*data_files) if data_files else "profile"
    indices = cache.get_or_build(data_hash, lambda: build_indices(repo.df, chunker))
    t_index = time.perf_counter()

    engine = HybridSearchEngine(indices=indices)
    engine.search("flood Bangladesh deaths")
    t_search = time.perf_counter()

    snapshot = tracemalloc.take_snapshot()
    tracemalloc.stop()

    rss_after = _get_rss_mb()
    top_stats = snapshot.statistics("lineno")

    results = {
        "rss_before_mb": rss_before,
        "rss_after_mb": rss_after,
        "rss_delta_mb": rss_after - rss_before,
        "load_time_s": t_load - t0,
        "index_time_s": t_index - t_load,
        "search_time_s": t_search - t_index,
        "total_time_s": t_search - t0,
        "record_count": len(repo.df),
        "top_allocations": [
            {"file": str(stat.traceback[0]), "size_kb": stat.size / 1024}
            for stat in top_stats[:5]
        ],
    }

    logger.info("=== Memory Profile Results ===")
    logger.info("RSS before:    %.1f MB", results["rss_before_mb"])
    logger.info("RSS after:     %.1f MB", results["rss_after_mb"])
    logger.info("RSS delta:     %.1f MB", results["rss_delta_mb"])
    logger.info("Load time:     %.2f s (%d records)", results["load_time_s"], results["record_count"])
    logger.info("Index time:    %.2f s", results["index_time_s"])
    logger.info("Search time:   %.3f s", results["search_time_s"])
    logger.info("Total time:    %.2f s", results["total_time_s"])
    logger.info("")
    logger.info("Recommended Docker memory limit: %.0f MB", rss_after * 1.5)
    logger.info("")
    logger.info("Top 5 memory allocations:")
    for stat in top_stats[:5]:
        logger.info("  %s: %.1f KB", stat.traceback[0], stat.size / 1024)

    return results


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path(__file__).parent.parent / "data" / "samples",
        help="Data directory (defaults to samples/)",
    )
    args = parser.parse_args()
    profile_startup(args.data_dir)


if __name__ == "__main__":
    main()
