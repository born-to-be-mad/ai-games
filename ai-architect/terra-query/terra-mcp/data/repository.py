"""Unified in-memory disaster data store backed by pandas."""

import logging
import time
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
        load_started = time.perf_counter()
        source_frames: dict[str, pd.DataFrame] = {}

        for loader in self._loaders:
            name = loader.source_name()
            source_start = time.perf_counter()
            df = loader.load()
            source_frames[name] = df
            logger.info(
                "Loaded %d records from %s in %.2fs",
                len(df),
                name,
                time.perf_counter() - source_start,
            )

        if not source_frames:
            logger.warning("No data loaded from any source")
            self._df = pd.DataFrame()
            return

        logger.info("Combining %d source DataFrames", len(source_frames))
        combine_start = time.perf_counter()
        combined = pd.concat(list(source_frames.values()), ignore_index=True)
        logger.info(
            "Combined into %d records in %.2fs",
            len(combined),
            time.perf_counter() - combine_start,
        )

        logger.info("Computing source quality stats")
        stats_start = time.perf_counter()
        source_stats = {
            name: compute_source_stats(df) for name, df in source_frames.items()
        }
        logger.info(
            "Computed source quality stats for %d sources in %.2fs",
            len(source_stats),
            time.perf_counter() - stats_start,
        )

        deduplicator = CrossSourceDeduplicator()
        logger.info("Starting cross-source deduplication")
        dedup_start = time.perf_counter()
        self._df = deduplicator.deduplicate(combined)
        logger.info(
            "Deduplication completed in %.2fs (records=%d)",
            time.perf_counter() - dedup_start,
            len(self._df),
        )

        logger.info("Emitting quality report")
        report_start = time.perf_counter()
        log_quality_report(self._df, source_stats)
        logger.info(
            "Quality report emitted in %.2fs", time.perf_counter() - report_start
        )
        logger.info(
            "Repository load completed in %.2fs (final records=%d)",
            time.perf_counter() - load_started,
            len(self._df),
        )

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
            mask = result["country"].str.lower().str.contains(
                country_lower, na=False
            ) | result["country_iso3"].str.lower().str.contains(country_lower, na=False)
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
