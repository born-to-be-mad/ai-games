"""Unit tests for DataNormalizer."""

import pandas as pd
import pytest

from data.quality.normalizer import DataNormalizer


@pytest.fixture()
def normalizer():
    return DataNormalizer()


class TestCountryToIso3:
    def test_known_override(self, normalizer):
        assert normalizer.country_to_iso3("Burma") == "MMR"
        assert normalizer.country_to_iso3("USA") == "USA"
        assert normalizer.country_to_iso3("united states") == "USA"

    def test_none_returns_none(self, normalizer):
        assert normalizer.country_to_iso3(None) is None

    def test_empty_returns_none(self, normalizer):
        assert normalizer.country_to_iso3("") is None

    def test_na_returns_none(self, normalizer):
        assert normalizer.country_to_iso3(pd.NA) is None

    def test_standard_country(self, normalizer):
        result = normalizer.country_to_iso3("Germany")
        assert result == "DEU"

    def test_case_insensitive(self, normalizer):
        assert normalizer.country_to_iso3("FRANCE") == normalizer.country_to_iso3(
            "france"
        )


class TestNullHandling:
    def test_zero_deaths_preserved(self, normalizer):
        """deaths=0 means zero confirmed deaths — must NOT become NaN."""
        df = pd.DataFrame({"deaths": [0.0, 5.0, pd.NA]})
        result = normalizer._normalize_numeric_fields(df)
        assert result.loc[0, "deaths"] == 0.0

    def test_nan_deaths_preserved(self, normalizer):
        """deaths=NaN means unknown — must NOT become 0."""
        df = pd.DataFrame({"deaths": [0.0, 5.0, pd.NA]})
        result = normalizer._normalize_numeric_fields(df)
        assert pd.isna(result.loc[2, "deaths"])

    def test_non_numeric_becomes_nan(self, normalizer):
        df = pd.DataFrame({"deaths": ["N/A", "unknown", "100"]})
        result = normalizer._normalize_numeric_fields(df)
        assert pd.isna(result.loc[0, "deaths"])
        assert pd.isna(result.loc[1, "deaths"])
        assert result.loc[2, "deaths"] == 100.0


class TestDateNormalization:
    def test_string_date_parsed(self, normalizer):
        df = pd.DataFrame({"start_date": ["1998-07-01", "2004-12-26"]})
        result = normalizer._normalize_dates(df)
        assert result.loc[0, "start_date"] == pd.Timestamp("1998-07-01")

    def test_invalid_date_becomes_nat(self, normalizer):
        df = pd.DataFrame({"start_date": ["not-a-date"]})
        result = normalizer._normalize_dates(df)
        assert pd.isna(result.loc[0, "start_date"])
