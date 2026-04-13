"""Unit tests for HierarchicalChunker."""
import pytest

from search.hierarchical_chunker import HierarchicalChunker


@pytest.fixture()
def chunker():
    return HierarchicalChunker()


class TestEnrichText:
    def test_basic_record(self, chunker):
        record = {
            "disaster_type": "flood",
            "country": "Bangladesh",
            "start_date": "1998-07-01",
            "deaths": 1050,
            "affected": 30_000_000,
            "economic_damage_usd": 5_900_000_000,
        }
        text = chunker.enrich_text(record)
        assert "flood" in text.lower()
        assert "Bangladesh" in text
        assert "1998" in text
        assert "1.1K" in text or "1,050" in text or "1050" in text
        assert "30.0M" in text or "30000000" in text

    def test_missing_deaths_says_unknown(self, chunker):
        import pandas as pd
        record = {
            "disaster_type": "drought",
            "country": "Zimbabwe",
            "start_date": "2020-01-01",
            "deaths": pd.NA,
        }
        text = chunker.enrich_text(record)
        assert "unknown" in text.lower()

    def test_minimal_record(self, chunker):
        text = chunker.enrich_text({"disaster_type": "earthquake", "country": "Japan"})
        assert "earthquake" in text.lower()
        assert "Japan" in text


class TestChunk:
    def test_short_text_self_parent(self, chunker):
        short_text = "A flood in Bangladesh in 1998."
        pairs = chunker.chunk(short_text)
        assert len(pairs) == 1
        assert pairs[0].child_text == pairs[0].parent_text

    def test_medium_text_single_parent(self, chunker):
        # ~50 tokens
        medium_text = " ".join(["word"] * 50)
        pairs = chunker.chunk(medium_text)
        # All pairs have the same parent (the full text)
        parent_ids = {p.parent_id for p in pairs}
        assert len(parent_ids) == 1

    def test_long_text_multiple_parents(self, chunker):
        # ~600 tokens — exceeds PARENT_SIZE=512
        long_text = " ".join([f"word{i}" for i in range(600)])
        pairs = chunker.chunk(long_text)
        parent_ids = {p.parent_id for p in pairs}
        assert len(parent_ids) > 1

    def test_empty_string_returns_one_pair(self, chunker):
        pairs = chunker.chunk("")
        assert len(pairs) == 1

    def test_metadata_passed_through(self, chunker):
        meta = {"event_id": "test_001", "disaster_type": "flood"}
        pairs = chunker.chunk("A short flood event.", metadata=meta)
        assert pairs[0].metadata == meta
