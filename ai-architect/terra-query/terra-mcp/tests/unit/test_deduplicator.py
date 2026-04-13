"""Unit tests for CrossSourceDeduplicator."""
import pandas as pd
import pytest

from data.loaders.base_loader import NORMALIZED_SCHEMA
from data.quality.deduplicator import CrossSourceDeduplicator


def _make_row(**kwargs) -> dict:
    base = {col: None for col in NORMALIZED_SCHEMA}
    base.update(kwargs)
    return base


@pytest.fixture()
def deduplicator():
    return CrossSourceDeduplicator()


class TestDeduplicate:
    def test_empty_df_passthrough(self, deduplicator):
        df = pd.DataFrame(columns=NORMALIZED_SCHEMA)
        result = deduplicator.deduplicate(df)
        assert result.empty

    def test_single_source_no_dedup(self, deduplicator):
        rows = [
            _make_row(event_id="a1", disaster_type="flood", country="Bangladesh",
                      source="eosdis", start_date=pd.Timestamp("1998-07-01"), deaths=1000),
            _make_row(event_id="a2", disaster_type="flood", country="Bangladesh",
                      source="eosdis", start_date=pd.Timestamp("1998-07-05"), deaths=500),
        ]
        df = pd.DataFrame(rows)
        result = deduplicator.deduplicate(df)
        # Single source — no cross-source dedup applied
        assert len(result) == 2

    def test_cross_source_duplicate_merged(self, deduplicator):
        rows = [
            _make_row(event_id="a1", disaster_type="earthquake", country="Indonesia",
                      source="eosdis", start_date=pd.Timestamp("2004-12-26"), deaths=165708),
            _make_row(event_id="b1", disaster_type="earthquake", country="Indonesia",
                      source="noaa", start_date=pd.Timestamp("2004-12-27"), deaths=150000),
        ]
        df = pd.DataFrame(rows)
        result = deduplicator.deduplicate(df)
        assert len(result) == 1
        # Should prefer higher deaths value
        assert result.iloc[0]["deaths"] == 165708

    def test_different_countries_not_merged(self, deduplicator):
        rows = [
            _make_row(event_id="a1", disaster_type="flood", country="Bangladesh",
                      source="eosdis", start_date=pd.Timestamp("2020-06-01"), deaths=100),
            _make_row(event_id="b1", disaster_type="flood", country="India",
                      source="noaa", start_date=pd.Timestamp("2020-06-01"), deaths=200),
        ]
        df = pd.DataFrame(rows)
        result = deduplicator.deduplicate(df)
        assert len(result) == 2

    def test_date_too_far_apart_not_merged(self, deduplicator):
        rows = [
            _make_row(event_id="a1", disaster_type="flood", country="India",
                      source="eosdis", start_date=pd.Timestamp("2020-01-01"), deaths=50),
            _make_row(event_id="b1", disaster_type="flood", country="India",
                      source="noaa", start_date=pd.Timestamp("2020-06-01"), deaths=60),
        ]
        df = pd.DataFrame(rows)
        result = deduplicator.deduplicate(df)
        assert len(result) == 2

    def test_source_field_combined(self, deduplicator):
        rows = [
            _make_row(event_id="a1", disaster_type="earthquake", country="Japan",
                      source="eosdis", start_date=pd.Timestamp("2011-03-11"), deaths=19846),
            _make_row(event_id="b1", disaster_type="earthquake", country="Japan",
                      source="noaa", start_date=pd.Timestamp("2011-03-11"), deaths=18000),
        ]
        df = pd.DataFrame(rows)
        result = deduplicator.deduplicate(df)
        assert len(result) == 1
        assert "eosdis" in result.iloc[0]["source"]
        assert "noaa" in result.iloc[0]["source"]
