"""Integration tests: load → normalize → deduplicate → index → search pipeline."""

import sys
from pathlib import Path

import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from data.loaders.eosdis_loader import EosdisLoader
from data.loaders.noaa_loader import NoaaLoader
from data.repository import DisasterRepository
from search.hierarchical_chunker import HierarchicalChunker

SAMPLES_DIR = Path(__file__).parent.parent.parent / "data" / "samples"


class TestLoaderPipeline:
    def test_eosdis_sample_loads(self):
        loader = EosdisLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        assert not df.empty
        assert "disaster_type" in df.columns
        assert "country" in df.columns
        assert "deaths" in df.columns

    def test_noaa_sample_loads(self):
        loader = NoaaLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        assert not df.empty
        assert "disaster_type" in df.columns
        assert df["country_iso3"].iloc[0] == "USA"

    def test_eosdis_dates_parsed(self):
        loader = EosdisLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        assert pd.api.types.is_datetime64_any_dtype(df["start_date"])

    def test_noaa_damage_converted(self):
        loader = NoaaLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        # Damage should be in USD (not thousands)
        non_null = df["economic_damage_usd"].dropna()
        if not non_null.empty:
            # NOAA values like 125M → 125,000,000 in USD
            assert non_null.max() > 1_000

    def test_disaster_type_lowercase(self):
        loader = EosdisLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        assert df["disaster_type"].str.islower().all()


class TestRepositoryPipeline:
    @pytest.fixture()
    def repo(self):
        r = DisasterRepository(
            loaders=[
                EosdisLoader(data_dir=SAMPLES_DIR),
                NoaaLoader(data_dir=SAMPLES_DIR),
            ]
        )
        r.load()
        return r

    def test_repository_loads_both_sources(self, repo):
        assert not repo.df.empty
        sources = repo.df["source"].unique()
        assert any("eosdis" in str(s) for s in sources)

    def test_query_by_type(self, repo):
        floods = repo.query(disaster_type="flood")
        assert all(floods["disaster_type"] == "flood")

    def test_query_by_country(self, repo):
        result = repo.query(country="Bangladesh")
        assert len(result) > 0

    def test_query_by_year_range(self, repo):
        result = repo.query(year_from=2000, year_to=2010)
        years = result["start_date"].dt.year.dropna()
        assert years.between(2000, 2010).all()


class TestEnrichAndChunk:
    def test_enriched_text_is_natural_language(self):
        loader = EosdisLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        chunker = HierarchicalChunker()
        record = df.iloc[0].to_dict()
        text = chunker.enrich_text(record)
        assert len(text) > 20
        assert any(w in text.lower() for w in ("in", "a", "deaths", "unknown"))

    def test_chunk_record_returns_pairs(self):
        loader = EosdisLoader(data_dir=SAMPLES_DIR)
        df = loader.load()
        chunker = HierarchicalChunker()
        record = df.iloc[0].to_dict()
        pairs = chunker.chunk_record(record)
        assert len(pairs) >= 1
        assert pairs[0].child_text
        assert pairs[0].parent_text
