"""Unit tests for EOSDIS loader real-dataset policy."""

from pathlib import Path

import pytest

from data.loaders.eosdis_loader import EosdisLoader


def test_missing_file_allowed_in_dev(monkeypatch, tmp_path: Path):
    monkeypatch.delenv("REQUIRE_REAL_EOSDIS", raising=False)
    monkeypatch.setenv("TERRA_QUERY_ENV", "dev")
    loader = EosdisLoader(data_dir=tmp_path)

    df = loader.load()

    assert df.empty


def test_missing_file_raises_when_required(monkeypatch, tmp_path: Path):
    monkeypatch.setenv("REQUIRE_REAL_EOSDIS", "true")
    loader = EosdisLoader(data_dir=tmp_path)

    with pytest.raises(FileNotFoundError):
        loader.load()
