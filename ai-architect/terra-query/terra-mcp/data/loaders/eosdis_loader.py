"""Loader for the Kaggle EOSDIS (EM-DAT-based) natural disasters CSV."""

import logging
import os
from pathlib import Path

import pandas as pd

from data.loaders.base_loader import BaseLoader
from data.quality.normalizer import DataNormalizer

logger = logging.getLogger(__name__)

# Default path — override with DATA_DIR env var
_DEFAULT_PATH = Path(__file__).parent.parent / "samples" / "eosdis_sample.csv"

# Column mapping from raw EOSDIS CSV to normalized schema
# Supports both legacy format (with "Dis No") and newer EM-DAT export format
_COLUMN_MAP = {
    "Dis No": "source_event_id",          # legacy format
    "Disaster Type": "disaster_type",
    "Disaster Subtype": "subtype",
    "Country": "country",
    "ISO": "country_iso3_raw",
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
    "Total Damage ('000 US$)": "economic_damage_usd",   # legacy
    "Total Damages ('000 US$)": "economic_damage_usd",  # newer EM-DAT export
    "Dis Mag Value": "magnitude",
    "Year": "_year",
    "Seq": "_seq",
}


class EosdisLoader(BaseLoader):
    """Loads the Kaggle EOSDIS (global, 1900–2021, ~22k events) CSV."""

    def __init__(self, data_dir: Path | None = None) -> None:
        data_dir = data_dir or Path(os.getenv("DATA_DIR", str(_DEFAULT_PATH.parent)))
        self._data_dir = data_dir
        self._real_path = data_dir / "eosdis.csv"
        self._sample_path = data_dir / "eosdis_sample.csv"
        self._path = self._real_path
        self._normalizer = DataNormalizer()

    def source_name(self) -> str:
        return "eosdis"

    def load(self) -> pd.DataFrame:
        enforce_real = _require_real_dataset()
        candidate = self._select_input_path(enforce_real)
        if candidate is None:
            expected_real = self._real_path
            if enforce_real:
                raise FileNotFoundError(
                    "EOSDIS real dataset is required but missing. "
                    f"Expected file: {expected_real}"
                )
            logger.warning(
                "EOSDIS file not found at %s — using empty frame (dev/test mode)",
                self._real_path,
            )
            return self._empty_frame()
        self._path = candidate

        logger.info(
            "Loading EOSDIS data from %s (mode=%s)",
            self._path,
            "real-required" if enforce_real else "dev-fallback-allowed",
        )
        try:
            raw = pd.read_csv(self._path, low_memory=False)
        except Exception as exc:
            if enforce_real:
                raise RuntimeError(
                    f"Failed to read required EOSDIS dataset: {self._path}"
                ) from exc
            logger.error("Failed to read EOSDIS CSV: %s", exc)
            return self._empty_frame()
        missing_required = self._missing_required_columns(raw.columns)
        if missing_required:
            message = (
                f"EOSDIS CSV at {self._path} is missing required columns: "
                f"{', '.join(sorted(missing_required))}"
            )
            if enforce_real:
                raise ValueError(message)
            logger.warning("%s", message)
            return self._empty_frame()

        df = raw.rename(
            columns={k: v for k, v in _COLUMN_MAP.items() if k in raw.columns}
        )

        # Build ISO dates from component columns
        df["start_date"] = self._build_date(
            df, "_start_year", "_start_month", "_start_day"
        )
        df["end_date"] = self._build_date(df, "_end_year", "_end_month", "_end_day")

        # Convert damage from thousands USD to USD
        if "economic_damage_usd" in df.columns:
            df["economic_damage_usd"] = (
                pd.to_numeric(df["economic_damage_usd"], errors="coerce") * 1_000
            )

        df["source"] = self.source_name()
        # Build source_event_id from legacy "Dis No" or newer Year+Seq composite
        if "source_event_id" not in df.columns or df["source_event_id"].isna().all():
            if "_year" in df.columns and "_seq" in df.columns:
                df["source_event_id"] = (
                    df["_year"].astype(str) + "-" + df["_seq"].astype(str)
                )
            else:
                df["source_event_id"] = df.index.astype(str)
        df["event_id"] = self.source_name() + "_" + df["source_event_id"].astype(str)
        # Use pre-mapped ISO3 if available, otherwise derive from country name
        if "country_iso3_raw" in df.columns:
            df["country_iso3"] = df["country_iso3_raw"].where(
                df["country_iso3_raw"].notna(), other=df["country"].apply(self._normalizer.country_to_iso3)
            )
        else:
            df["country_iso3"] = df["country"].apply(self._normalizer.country_to_iso3)
        df["disaster_type"] = df["disaster_type"].str.lower().str.strip()

        df = self._ensure_schema(df)
        df = self._normalizer.normalize(df)
        if enforce_real and df.empty:
            raise ValueError(
                f"EOSDIS dataset produced zero normalized records in required mode: {self._path}"
            )

        logger.info("EOSDIS: loaded %d records", len(df))
        return df

    def _select_input_path(self, enforce_real: bool) -> Path | None:
        if enforce_real:
            return self._real_path if self._real_path.exists() else None
        if self._real_path.exists():
            return self._real_path
        if self._sample_path.exists():
            return self._sample_path
        return None

    @staticmethod
    def _missing_required_columns(columns: pd.Index) -> set[str]:
        # Accept either legacy "Dis No" id or newer "Year"+"Seq" composite id
        has_id = "Dis No" in columns or ("Year" in columns and "Seq" in columns)
        required_fields = {"Disaster Type", "Country", "Start Year"}
        missing = required_fields - set(columns)
        if not has_id:
            missing.add("Dis No")
        return missing

    @staticmethod
    def _build_date(df: pd.DataFrame, yr: str, mo: str, dy: str) -> pd.Series:
        if yr not in df.columns:
            return pd.Series([pd.NaT] * len(df))
        year = pd.to_numeric(df.get(yr), errors="coerce")
        month = (
            pd.to_numeric(df.get(mo, pd.Series([1] * len(df))), errors="coerce")
            .fillna(1)
            .astype(int)
        )
        day = (
            pd.to_numeric(df.get(dy, pd.Series([1] * len(df))), errors="coerce")
            .fillna(1)
            .astype(int)
        )
        dates = []
        for y, m, d in zip(year, month, day):
            try:
                dates.append(
                    pd.Timestamp(int(y), int(m), int(d)) if pd.notna(y) else pd.NaT
                )
            except Exception:
                dates.append(pd.NaT)
        return pd.Series(dates)


def _require_real_dataset() -> bool:
    """
    Enforce real EOSDIS dataset based on explicit mode/env policy.
    Controls:
      - DATA_SOURCE=real|dev|auto (highest priority)
      - REQUIRE_REAL_EOSDIS=true|false
      - APP_ENV/TERRA_QUERY_ENV in {dev,test,local} allows sample fallback
    """
    mode = os.getenv("DATA_SOURCE", "auto").strip().lower()
    if mode == "real":
        return True
    if mode == "dev":
        return False
    if mode not in {"", "auto"}:
        logger.warning(
            "Unknown DATA_SOURCE=%r, falling back to auto policy", mode
        )
    explicit = os.getenv("REQUIRE_REAL_EOSDIS")
    if explicit is not None:
        return explicit.strip().lower() in {"1", "true", "yes", "on"}
    env = os.getenv("TERRA_QUERY_ENV", os.getenv("APP_ENV", "dev")).strip().lower()
    return env not in {"dev", "test", "local"}
