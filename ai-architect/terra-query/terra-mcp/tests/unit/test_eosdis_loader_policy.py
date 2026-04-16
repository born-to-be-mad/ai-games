"""Unit tests for EOSDIS loader real-dataset policy."""

from pathlib import Path

import pandas as pd
import pytest

from data.loaders.eosdis_loader import EosdisLoader


def test_missing_file_allowed_in_dev(monkeypatch, tmp_path: Path):
    monkeypatch.setenv("DATA_SOURCE", "dev")
    monkeypatch.delenv("REQUIRE_REAL_EOSDIS", raising=False)
    monkeypatch.setenv("TERRA_QUERY_ENV", "dev")
    loader = EosdisLoader(data_dir=tmp_path)

    df = loader.load()

    assert df.empty


def test_missing_file_raises_when_required(monkeypatch, tmp_path: Path):
    monkeypatch.setenv("DATA_SOURCE", "real")
    loader = EosdisLoader(data_dir=tmp_path)

    with pytest.raises(FileNotFoundError):
        loader.load()


def test_data_source_real_ignores_sample_file(monkeypatch, tmp_path: Path):
    monkeypatch.setenv("DATA_SOURCE", "real")
    sample = pd.DataFrame(
        [{"Dis No": "S-1", "Disaster Type": "Flood", "Country": "Bangladesh", "Start Year": 2001}]
    )
    sample.to_csv(tmp_path / "eosdis_sample.csv", index=False)
    loader = EosdisLoader(data_dir=tmp_path)

    with pytest.raises(FileNotFoundError):
        loader.load()


def test_data_source_dev_overrides_require_real(monkeypatch, tmp_path: Path):
    monkeypatch.setenv("DATA_SOURCE", "dev")
    monkeypatch.setenv("REQUIRE_REAL_EOSDIS", "true")
    loader = EosdisLoader(data_dir=tmp_path)

    df = loader.load()

    assert df.empty


def test_required_mode_fails_on_invalid_schema(monkeypatch, tmp_path: Path):
    monkeypatch.setenv("DATA_SOURCE", "real")
    pd.DataFrame([{"Dis No": "X-1"}]).to_csv(tmp_path / "eosdis.csv", index=False)
    loader = EosdisLoader(data_dir=tmp_path)

    with pytest.raises(ValueError, match="missing required columns"):
        loader.load()
