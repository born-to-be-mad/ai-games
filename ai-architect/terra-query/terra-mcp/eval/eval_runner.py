"""CLI entrypoint for TerraQuery RAGAS evaluation.

Usage:
  python -m eval.eval_runner [--subset canary|full] [--output-file PATH]

Subset:
  canary  — first 10 questions (run on every PR)
  full    — all 30 questions (run nightly)

Exit codes:
  0  all metrics >= target threshold (PASS)
  1  any metric < min threshold (HARD FAIL — blocks CI)
  2  some metrics below target but above min (WARN — prints but does not block)
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from pathlib import Path

# Ensure project root on path
sys.path.insert(0, str(Path(__file__).parent.parent))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

_GOLDEN_DATASET_PATH = Path(__file__).parent / "golden_dataset.json"


def load_golden_dataset(subset: str, config) -> list[dict]:
    with open(_GOLDEN_DATASET_PATH, encoding="utf-8") as f:
        entries: list[dict] = json.load(f)
    size = config.canary_size if subset == "canary" else config.full_size
    return entries[:size]


def build_retriever(config):
    """Build a McpDirectRetriever using the real terra-mcp data pipeline."""
    from data.repository import DisasterRepository
    from data.index.index_builder import build_indices
    from data.index.index_cache import IndexCache
    from search.hierarchical_chunker import HierarchicalChunker
    from search.hybrid_search import HybridSearchEngine
    from search.rrf_config import RRFConfig
    from eval.answer_generator import McpDirectRetriever

    logger.info("Loading disaster data for evaluation...")
    repo = DisasterRepository()
    repo.load()

    engine = None
    if not repo.df.empty:
        chunker = HierarchicalChunker()
        cache = IndexCache()
        data_files = repo.get_data_file_paths()
        data_hash = IndexCache.compute_data_hash(*data_files) if data_files else "default"
        indices = cache.get_or_build(data_hash, lambda: build_indices(repo.df, chunker))
        repo.set_indices(indices)
        engine = HybridSearchEngine(indices=indices, config=RRFConfig.from_env())
        logger.info("Search engine ready")

    return McpDirectRetriever(repo, engine, top_k=config.top_k_contexts)


def write_github_summary(summary_md: str) -> None:
    """Append RAGAS results to GitHub Actions job summary if running in CI."""
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as f:
            f.write("\n## RAGAS Evaluation Results\n\n")
            f.write(summary_md)
            f.write("\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="TerraQuery RAGAS evaluator")
    parser.add_argument(
        "--subset",
        choices=["canary", "full"],
        default=os.environ.get("EVAL_SUBSET", "canary"),
        help="canary=10 questions (PR), full=30 questions (nightly)",
    )
    parser.add_argument(
        "--output-file",
        default=None,
        help="Optional JSON file path to write full results",
    )
    args = parser.parse_args(argv)

    from eval.eval_config import EvalConfig
    from eval.answer_generator import AnswerGenerator, build_openai_generator_fn
    from eval.ragas_evaluator import RagasEvaluator

    config = EvalConfig.from_env()
    try:
        config.validate()
    except ValueError as exc:
        logger.error("Config error: %s", exc)
        return 1

    entries = load_golden_dataset(args.subset, config)
    logger.info("Running %s eval — %d questions", args.subset, len(entries))

    retriever = build_retriever(config)
    generator_fn = build_openai_generator_fn(config.generator_model, config.openai_api_key)
    generator = AnswerGenerator(retriever, generator_fn)

    logger.info("Generating answers with %s...", config.generator_model)
    samples = generator.generate_batch(entries)

    if not samples:
        logger.error("No samples generated — aborting evaluation")
        return 1

    logger.info("Running RAGAS evaluation with %s...", config.evaluator_model)
    evaluator = RagasEvaluator(config)
    result = evaluator.evaluate(samples)

    print(result.to_summary())
    write_github_summary(result.to_github_summary())

    if args.output_file:
        out = {
            "subset": args.subset,
            "sample_count": result.sample_count,
            "metrics": [
                {
                    "name": m.name,
                    "score": m.score,
                    "threshold": m.threshold,
                    "min_threshold": m.min_threshold,
                    "status": m.status,
                }
                for m in result.metric_scores
            ],
        }
        Path(args.output_file).write_text(json.dumps(out, indent=2), encoding="utf-8")
        logger.info("Results written to %s", args.output_file)

    if result.any_hard_fail:
        logger.error("HARD FAIL: one or more metrics below minimum threshold")
        return 1
    if not result.all_pass:
        logger.warning("WARN: some metrics below target threshold (above minimum)")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
