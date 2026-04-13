"""Data quality report logged at startup."""
import logging
from typing import Any

import pandas as pd

logger = logging.getLogger(__name__)


def log_quality_report(df: pd.DataFrame, source_stats: dict[str, dict[str, Any]]) -> None:
    """Log a concise data quality report after loading and merging all sources."""
    logger.info("=== TerraQuery Data Quality Report ===")

    for source, stats in source_stats.items():
        count = stats.get("count", 0)
        missing_deaths = stats.get("missing_deaths", 0)
        missing_pct = (missing_deaths / count * 100) if count > 0 else 0
        dup_ids = stats.get("duplicate_event_ids", 0)
        logger.info(
            "%s: %d records loaded, %d with missing death toll (%.1f%%), %d duplicate event_ids",
            source.upper(), count, missing_deaths, missing_pct, dup_ids,
        )

    total_before = sum(s.get("count", 0) for s in source_stats.values())
    total_after = len(df)
    deduped = total_before - total_after
    logger.info(
        "Merged: %d total → %d after dedup (%d cross-source duplicates)",
        total_before, total_after, deduped,
    )

    # Coverage gap analysis: countries with long gaps
    _log_coverage_gaps(df)
    logger.info("=========================================")


def _log_coverage_gaps(df: pd.DataFrame) -> None:
    """Warn about countries with suspicious zero-event periods (≥5 years)."""
    if df.empty or "start_date" not in df.columns or "country_iso3" not in df.columns:
        return

    df_dated = df.dropna(subset=["start_date", "country_iso3"])
    if df_dated.empty:
        return

    df_dated = df_dated.copy()
    df_dated["year"] = df_dated["start_date"].dt.year

    for country, grp in df_dated.groupby("country_iso3"):
        years = sorted(grp["year"].dropna().unique())
        if len(years) < 2:
            continue
        gaps = [(years[i], years[i + 1]) for i in range(len(years) - 1)
                if years[i + 1] - years[i] > 5]
        for start_yr, end_yr in gaps[:2]:  # log at most 2 gaps per country
            logger.info(
                "Coverage gap: %s %d–%d (%d years, likely data gap not reality)",
                country, start_yr, end_yr, end_yr - start_yr,
            )


def compute_source_stats(df: pd.DataFrame) -> dict[str, Any]:
    """Compute quality stats for a single-source DataFrame."""
    count = len(df)
    missing_deaths = int(df["deaths"].isna().sum()) if "deaths" in df.columns else 0
    duplicate_ids = int(df["event_id"].duplicated().sum()) if "event_id" in df.columns else 0
    return {
        "count": count,
        "missing_deaths": missing_deaths,
        "duplicate_event_ids": duplicate_ids,
    }
