"""Unified in-memory disaster data store backed by pandas."""
import logging
from pathlib import Path
from typing import TYPE_CHECKING

import pandas as pd

from data.loaders.base_loader import BaseLoader
from data.loaders.eosdis_loader import EosdisLoader
from data.loaders.noaa_loader import NoaaLoader
from data.quality.deduplicator import CrossSourceDeduplicator
from data.quality.quality_report import compute_source_stats, log_quality_report

if TYPE_CHECKING:
    from data.index.index_builder import SearchIndices

logger = logging.getLogger(__name__)


class DisasterRepository:
    """
    Loads, merges, and deduplicates disaster data from all registered sources.
    Provides filtered access to the unified DataFrame for tool functions.
    """

    def __init__(self, loaders: list[BaseLoader] | None = None) -> None:
        self._loaders: list[BaseLoader] = loaders or [EosdisLoader(), NoaaLoader()]
        self._df: pd.DataFrame = pd.DataFrame()
        self._indices: "SearchIndices | None" = None

    def load(self) -> None:
        """Load all sources, deduplicate, and store unified DataFrame."""
        source_frames: dict[str, pd.DataFrame] = {}

        for loader in self._loaders:
            name = loader.source_name()
            df = loader.load()
            source_frames[name] = df
            logger.info("Loaded %d records from %s", len(df), name)

        if not source_frames:
            logger.warning("No data loaded from any source")
            self._df = pd.DataFrame()
            return

        combined = pd.concat(list(source_frames.values()), ignore_index=True)
        source_stats = {name: compute_source_stats(df) for name, df in source_frames.items()}

        deduplicator = CrossSourceDeduplicator()
        self._df = deduplicator.deduplicate(combined)

        log_quality_report(self._df, source_stats)

    def set_indices(self, indices: "SearchIndices") -> None:
        self._indices = indices

    def get_indices(self) -> "SearchIndices | None":
        return self._indices

    @property
    def df(self) -> pd.DataFrame:
        return self._df

    def query(
        self,
        disaster_type: str | None = None,
        country: str | None = None,
        year_from: int | None = None,
        year_to: int | None = None,
    ) -> pd.DataFrame:
        """Return filtered DataFrame. All filters are optional."""
        result = self._df.copy()

        if disaster_type:
            dt = disaster_type.lower()
            result = result[result["disaster_type"].str.lower() == dt]

        if country:
            country_lower = country.lower()
            mask = (
                result["country"].str.lower().str.contains(country_lower, na=False)
                | result["country_iso3"].str.lower().str.contains(country_lower, na=False)
            )
            result = result[mask]

        if year_from is not None and "start_date" in result.columns:
            result = result[result["start_date"].dt.year >= year_from]

        if year_to is not None and "start_date" in result.columns:
            result = result[result["start_date"].dt.year <= year_to]

        return result

    def get_data_file_paths(self) -> list[Path]:
        """Return paths of all loaded data files (for index cache hash)."""
        paths = []
        for loader in self._loaders:
            if hasattr(loader, "_path"):
                paths.append(loader._path)
        return paths
