"""Unit tests for IndexCache."""
from unittest.mock import MagicMock

import pytest

from data.index.index_cache import IndexCache


@pytest.fixture()
def tmp_cache(tmp_path):
    return IndexCache(cache_dir=tmp_path)


class TestIndexCache:
    def test_cache_miss_calls_builder(self, tmp_cache):
        builder = MagicMock(return_value={"bm25": "mock_bm25", "faiss": "mock_faiss"})
        result = tmp_cache.get_or_build("abc123", builder)
        builder.assert_called_once()
        assert result == {"bm25": "mock_bm25", "faiss": "mock_faiss"}

    def test_cache_hit_skips_builder(self, tmp_cache):
        builder = MagicMock(return_value={"result": "built"})
        # First call — builds and caches
        tmp_cache.get_or_build("xyz789", builder)
        # Second call — should load from cache
        builder2 = MagicMock(return_value={"result": "rebuilt"})
        result = tmp_cache.get_or_build("xyz789", builder2)
        builder2.assert_not_called()
        assert result == {"result": "built"}

    def test_different_hashes_build_separately(self, tmp_cache):
        b1 = MagicMock(return_value="result_a")
        b2 = MagicMock(return_value="result_b")
        r1 = tmp_cache.get_or_build("hash_a", b1)
        r2 = tmp_cache.get_or_build("hash_b", b2)
        assert r1 == "result_a"
        assert r2 == "result_b"

    def test_invalidate_removes_files(self, tmp_cache):
        tmp_cache.get_or_build("toremove", lambda: "data")
        assert len(list(tmp_cache.cache_dir.glob("index_*.pkl"))) == 1
        tmp_cache.invalidate()
        assert len(list(tmp_cache.cache_dir.glob("index_*.pkl"))) == 0

    def test_compute_data_hash_deterministic(self, tmp_path):
        f = tmp_path / "test.csv"
        f.write_text("event_id,type\n1,flood\n")
        h1 = IndexCache.compute_data_hash(f)
        h2 = IndexCache.compute_data_hash(f)
        assert h1 == h2
        assert len(h1) == 32  # MD5 hex

    def test_compute_data_hash_changes_on_content(self, tmp_path):
        f = tmp_path / "data.csv"
        f.write_text("row1")
        h1 = IndexCache.compute_data_hash(f)
        f.write_text("row1_modified")
        h2 = IndexCache.compute_data_hash(f)
        assert h1 != h2

    def test_missing_file_ignored_in_hash(self, tmp_path):
        missing = tmp_path / "nonexistent.csv"
        # Should not raise, just skip missing file
        h = IndexCache.compute_data_hash(missing)
        assert isinstance(h, str)
