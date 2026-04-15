"""CrossSourceDeduplicator: fuzzy dedup of events across data sources."""

import logging
from typing import Any

import pandas as pd

logger = logging.getLogger(__name__)

SIMILARITY_THRESHOLD = 85  # rapidfuzz score 0–100
DATE_TOLERANCE_DAYS = 7  # events within 7 days = potential duplicate


class CrossSourceDeduplicator:
    """
    Identifies and merges duplicate events across data sources.
    Uses fuzzy matching on (location + disaster_type) + 7-day date tolerance.
    Groups by disaster_type to reduce O(n²) comparisons.
    """

    def deduplicate(self, df: pd.DataFrame) -> pd.DataFrame:
        if df.empty:
            return df

        before = len(df)
        # Only cross-source dedup is meaningful
        if df["source"].nunique() < 2:
            return df

        groups = df.groupby("disaster_type", group_keys=False)
        deduped_parts = [self._deduplicate_group(group) for _, group in groups]
        result = pd.concat(deduped_parts, ignore_index=True)

        removed = before - len(result)
        logger.info(
            "Deduplication: %d → %d records (%d cross-source duplicates merged)",
            before,
            len(result),
            removed,
        )
        return result

    def _deduplicate_group(self, group: pd.DataFrame) -> pd.DataFrame:
        """Deduplicate within a single disaster_type group."""
        try:
            from rapidfuzz import fuzz
        except ImportError:
            logger.debug("rapidfuzz not installed — skipping deduplication")
            return group

        records = group.to_dict("records")
        merged_flags = [False] * len(records)
        result = []

        for i, rec_a in enumerate(records):
            if merged_flags[i]:
                continue
            merged = dict(rec_a)
            for j in range(i + 1, len(records)):
                if merged_flags[j]:
                    continue
                rec_b = records[j]
                # Skip same-source duplicates (handled by source-level dedup)
                if rec_a.get("source") == rec_b.get("source"):
                    continue
                if self._is_duplicate(rec_a, rec_b, fuzz):
                    merged = self._merge_records(merged, rec_b)
                    merged_flags[j] = True
            result.append(merged)

        return pd.DataFrame(result)

    def _is_duplicate(self, a: dict, b: dict, fuzz: Any) -> bool:
        # 1. Location similarity
        loc_a = str(a.get("country", "") or "")
        loc_b = str(b.get("country", "") or "")
        if fuzz.token_set_ratio(loc_a, loc_b) < SIMILARITY_THRESHOLD:
            return False

        # 2. Date overlap within tolerance
        date_a = a.get("start_date")
        date_b = b.get("start_date")
        if pd.notna(date_a) and pd.notna(date_b):
            diff = abs((pd.Timestamp(date_a) - pd.Timestamp(date_b)).days)
            if diff > DATE_TOLERANCE_DAYS:
                return False

        return True

    @staticmethod
    def _merge_records(a: dict, b: dict) -> dict:
        """Merge two duplicate records; prefer non-null and higher numeric values."""
        from data.loaders.base_loader import NORMALIZED_SCHEMA

        merged: dict = {}
        for col in NORMALIZED_SCHEMA:
            val_a, val_b = a.get(col), b.get(col)
            if col in ("deaths", "injured", "affected", "economic_damage_usd"):
                # Prefer higher value (more complete reporting)
                na_a = val_a is None or pd.isna(val_a)
                na_b = val_b is None or pd.isna(val_b)
                if na_a and na_b:
                    merged[col] = None
                elif na_a:
                    merged[col] = val_b
                elif na_b:
                    merged[col] = val_a
                else:
                    merged[col] = max(float(val_a), float(val_b))
            else:
                # Prefer non-null value from a
                merged[col] = (
                    val_a if (val_a is not None and not _is_na(val_a)) else val_b
                )

        # Track merged sources
        src_a = str(a.get("source", ""))
        src_b = str(b.get("source", ""))
        merged["source"] = f"{src_a}+{src_b}" if src_a != src_b else src_a
        return merged


def _is_na(val: Any) -> bool:
    try:
        return pd.isna(val)
    except (TypeError, ValueError):
        return False
