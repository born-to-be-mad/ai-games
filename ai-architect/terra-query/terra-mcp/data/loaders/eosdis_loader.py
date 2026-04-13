"""Loader for the Kaggle EOSDIS (EM-DAT-based) natural disasters CSV."""
import logging
import os
from pathlib import Path

import pandas as pd

from data.loaders.base_loader import BaseLoader, NORMALIZED_SCHEMA
from data.quality.normalizer import DataNormalizer

logger = logging.getLogger(__name__)

# Default path — override with DATA_DIR env var
_DEFAULT_PATH = Path(__file__).parent.parent / "samples" / "eosdis_sample.csv"

# Column mapping from raw EOSDIS CSV to normalized schema
_COLUMN_MAP = {
    "Dis No": "source_event_id",
    "Disaster Type": "disaster_type",
    "Disaster Subtype": "subtype",
    "Country": "country",
    "Region": "region",
    "Start Year": "_start_year",
    "Start Month": "_start_month",
    "Start Day": "_start_day",
    "End Year": "_end_year",
    "End Month": "_end_month",
    "End Day": "_end_day",
    "Total Deaths": "deaths",
    "No Injured": "injured",
    "Total Affected": "affected",
    "Total Damage ('000 US$)": "economic_damage_usd",
    "Dis Mag Value": "magnitude",
}


class EosdisLoader(BaseLoader):
    """Loads the Kaggle EOSDIS (global, 1900–2021, ~22k events) CSV."""

    def __init__(self, data_dir: Path | None = None) -> None:
        data_dir = data_dir or Path(os.getenv("DATA_DIR", str(_DEFAULT_PATH.parent)))
        self._path = data_dir / "eosdis_sample.csv"
        if not self._path.exists():
            # fall back to full file name used after download
            full = data_dir / "eosdis.csv"
            if full.exists():
                self._path = full
        self._normalizer = DataNormalizer()

    def source_name(self) -> str:
        return "eosdis"

    def load(self) -> pd.DataFrame:
        if not self._path.exists():
            logger.warning("EOSDIS file not found at %s — skipping", self._path)
            return self._empty_frame()

        logger.info("Loading EOSDIS data from %s", self._path)
        try:
            raw = pd.read_csv(self._path, low_memory=False)
        except Exception as exc:
            logger.error("Failed to read EOSDIS CSV: %s", exc)
            return self._empty_frame()

        df = raw.rename(columns={k: v for k, v in _COLUMN_MAP.items() if k in raw.columns})

        # Build ISO dates from component columns
        df["start_date"] = self._build_date(df, "_start_year", "_start_month", "_start_day")
        df["end_date"] = self._build_date(df, "_end_year", "_end_month", "_end_day")

        # Convert damage from thousands USD to USD
        if "economic_damage_usd" in df.columns:
            df["economic_damage_usd"] = pd.to_numeric(df["economic_damage_usd"], errors="coerce") * 1_000

        df["source"] = self.source_name()
        df["event_id"] = self.source_name() + "_" + df.index.astype(str)
        df["country_iso3"] = df["country"].apply(self._normalizer.country_to_iso3)
        df["disaster_type"] = df["disaster_type"].str.lower().str.strip()

        df = self._ensure_schema(df)
        df = self._normalizer.normalize(df)

        logger.info("EOSDIS: loaded %d records", len(df))
        return df

    @staticmethod
    def _build_date(df: pd.DataFrame, yr: str, mo: str, dy: str) -> pd.Series:
        if yr not in df.columns:
            return pd.Series([pd.NaT] * len(df))
        year = pd.to_numeric(df.get(yr), errors="coerce")
        month = pd.to_numeric(df.get(mo, pd.Series([1] * len(df))), errors="coerce").fillna(1).astype(int)
        day = pd.to_numeric(df.get(dy, pd.Series([1] * len(df))), errors="coerce").fillna(1).astype(int)
        dates = []
        for y, m, d in zip(year, month, day):
            try:
                dates.append(pd.Timestamp(int(y), int(m), int(d)) if pd.notna(y) else pd.NaT)
            except Exception:
                dates.append(pd.NaT)
        return pd.Series(dates)
