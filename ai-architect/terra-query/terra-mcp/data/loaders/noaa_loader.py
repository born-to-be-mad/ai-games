"""Loader for NOAA Storm Events database CSV."""
import logging
import os
from pathlib import Path

import pandas as pd

from data.loaders.base_loader import BaseLoader, NORMALIZED_SCHEMA
from data.quality.normalizer import DataNormalizer

logger = logging.getLogger(__name__)

_DEFAULT_PATH = Path(__file__).parent.parent / "samples" / "noaa_sample.csv"

# NOAA Storm Events standard column names
_COLUMN_MAP = {
    "EVENT_ID": "source_event_id",
    "EVENT_TYPE": "disaster_type",
    "STATE": "_state",
    "BEGIN_DATE_TIME": "start_date",
    "END_DATE_TIME": "end_date",
    "DEATHS_DIRECT": "_deaths_direct",
    "DEATHS_INDIRECT": "_deaths_indirect",
    "INJURIES_DIRECT": "_injuries_direct",
    "INJURIES_INDIRECT": "_injuries_indirect",
    "DAMAGE_PROPERTY": "_damage_property",
    "DAMAGE_CROPS": "_damage_crops",
    "MAGNITUDE": "magnitude",
    "EPISODE_NARRATIVE": "_narrative",
}

# NOAA event types → normalized disaster_type
_TYPE_MAP = {
    "flood": "flood",
    "flash flood": "flood",
    "coastal flood": "flood",
    "lakeshore flood": "flood",
    "tornado": "storm",
    "hurricane": "storm",
    "tropical storm": "storm",
    "thunderstorm wind": "storm",
    "high wind": "storm",
    "winter storm": "storm",
    "blizzard": "storm",
    "ice storm": "storm",
    "hail": "storm",
    "heavy snow": "storm",
    "drought": "drought",
    "wildfire": "wildfire",
    "debris flow": "landslide",
    "landslide": "landslide",
    "avalanche": "landslide",
    "dust devil": "storm",
    "dust storm": "storm",
    "fog": "storm",
    "earthquake": "earthquake",
    "tsunami": "flood",
    "seiche": "flood",
    "volcanic ash": "volcano",
    "excessive heat": "extreme_temperature",
    "heat": "extreme_temperature",
    "cold/wind chill": "extreme_temperature",
    "extreme cold/wind chill": "extreme_temperature",
    "lightning": "storm",
    "waterspout": "storm",
    "rip current": "storm",
    "marine high wind": "storm",
    "marine thunderstorm wind": "storm",
}


def _parse_damage(val) -> float | None:
    """Convert NOAA damage strings like '150K', '2.5M', '1B' → USD float."""
    if pd.isna(val) or val == "" or val == "0.00K":
        return None
    s = str(val).strip().upper()
    try:
        if s.endswith("K"):
            return float(s[:-1]) * 1_000
        if s.endswith("M"):
            return float(s[:-1]) * 1_000_000
        if s.endswith("B"):
            return float(s[:-1]) * 1_000_000_000
        return float(s)
    except ValueError:
        return None


class NoaaLoader(BaseLoader):
    """Loads NOAA Storm Events (USA, 1950–present) CSV."""

    def __init__(self, data_dir: Path | None = None) -> None:
        data_dir = data_dir or Path(os.getenv("DATA_DIR", str(_DEFAULT_PATH.parent)))
        self._path = data_dir / "noaa_sample.csv"
        if not self._path.exists():
            full = data_dir / "noaa.csv"
            if full.exists():
                self._path = full
        self._normalizer = DataNormalizer()

    def source_name(self) -> str:
        return "noaa"

    def load(self) -> pd.DataFrame:
        if not self._path.exists():
            logger.warning("NOAA file not found at %s — skipping", self._path)
            return self._empty_frame()

        logger.info("Loading NOAA data from %s", self._path)
        try:
            raw = pd.read_csv(self._path, low_memory=False)
        except Exception as exc:
            logger.error("Failed to read NOAA CSV: %s", exc)
            return self._empty_frame()

        df = raw.rename(columns={k: v for k, v in _COLUMN_MAP.items() if k in raw.columns})

        # Deaths = direct + indirect
        d_dir = pd.to_numeric(df.get("_deaths_direct", 0), errors="coerce").fillna(0)
        d_ind = pd.to_numeric(df.get("_deaths_indirect", 0), errors="coerce").fillna(0)
        df["deaths"] = (d_dir + d_ind).where(d_dir + d_ind > 0, other=pd.NA)

        i_dir = pd.to_numeric(df.get("_injuries_direct", 0), errors="coerce").fillna(0)
        i_ind = pd.to_numeric(df.get("_injuries_indirect", 0), errors="coerce").fillna(0)
        df["injured"] = (i_dir + i_ind).where(i_dir + i_ind > 0, other=pd.NA)

        # Property + crop damage → economic_damage_usd
        prop = df.get("_damage_property", pd.Series(dtype=str)).apply(_parse_damage)
        crop = df.get("_damage_crops", pd.Series(dtype=str)).apply(_parse_damage)
        df["economic_damage_usd"] = prop.fillna(0) + crop.fillna(0)
        df["economic_damage_usd"] = df["economic_damage_usd"].where(
            df["economic_damage_usd"] > 0, other=pd.NA
        )

        df["affected"] = pd.NA  # NOAA doesn't report affected population
        df["country"] = "United States"
        df["country_iso3"] = "USA"
        df["region"] = df.get("_state", pd.NA)
        df["subtype"] = pd.NA
        df["source"] = self.source_name()
        df["event_id"] = self.source_name() + "_" + df.index.astype(str)

        # Normalize disaster type
        df["disaster_type"] = (
            df["disaster_type"]
            .str.lower()
            .str.strip()
            .map(lambda t: _TYPE_MAP.get(t, t))
        )

        df = self._ensure_schema(df)
        df = self._normalizer.normalize(df)

        logger.info("NOAA: loaded %d records", len(df))
        return df
