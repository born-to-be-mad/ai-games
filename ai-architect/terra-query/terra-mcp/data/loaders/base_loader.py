"""Abstract base class for all disaster data loaders."""
from abc import ABC, abstractmethod
import pandas as pd

NORMALIZED_SCHEMA = [
    "event_id",
    "disaster_type",
    "subtype",
    "country",
    "country_iso3",
    "region",
    "start_date",
    "end_date",
    "deaths",
    "injured",
    "affected",
    "economic_damage_usd",
    "magnitude",
    "source",
    "source_event_id",
]


class BaseLoader(ABC):
    """Pluggable loader interface — implement to add new data sources."""

    @abstractmethod
    def load(self) -> pd.DataFrame:
        """Load raw data and return DataFrame normalized to NORMALIZED_SCHEMA.

        Columns not available in the source must be present but set to NaN.
        Returns empty DataFrame (with correct columns) on load failure.
        """

    @abstractmethod
    def source_name(self) -> str:
        """Unique short identifier for this source, e.g. 'eosdis', 'noaa'."""

    def _empty_frame(self) -> pd.DataFrame:
        return pd.DataFrame(columns=NORMALIZED_SCHEMA)

    def _ensure_schema(self, df: pd.DataFrame) -> pd.DataFrame:
        """Add any missing normalized columns as NaN."""
        for col in NORMALIZED_SCHEMA:
            if col not in df.columns:
                df[col] = pd.NA
        return df[NORMALIZED_SCHEMA].copy()
