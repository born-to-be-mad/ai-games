"""Unit tests for disaster_query tool logic."""
import pytest

from tools.disaster_query import query_disasters_logic


class TestQueryDisasters:
    def test_no_filters_returns_all(self, mock_repo):
        result = query_disasters_logic(mock_repo)
        assert "Found" in result

    def test_filter_by_type(self, mock_repo):
        result = query_disasters_logic(mock_repo, disaster_type="flood")
        assert "flood" in result.lower()
        assert "earthquake" not in result.lower()

    def test_filter_by_country(self, mock_repo):
        result = query_disasters_logic(mock_repo, country="Bangladesh")
        assert "Bangladesh" in result

    def test_filter_by_year_range(self, mock_repo):
        result = query_disasters_logic(mock_repo, year_from=2000, year_to=2015)
        # Should include 2004 earthquake and 2010 events
        assert "Found" in result

    def test_invalid_disaster_type_returns_error(self, mock_repo):
        result = query_disasters_logic(mock_repo, disaster_type="alien_attack")
        assert "invalid" in result.lower() or "unknown" in result.lower()

    def test_limit_respected(self, mock_repo, sample_df):
        result = query_disasters_logic(mock_repo, limit=1)
        # Should mention only 1 record
        assert "1 disaster" in result.lower()

    def test_no_results_graceful(self, mock_repo):
        result = query_disasters_logic(mock_repo, disaster_type="epidemic", country="Antarctica")
        assert "no" in result.lower()

    def test_sorted_most_recent_first(self, mock_repo):
        result = query_disasters_logic(mock_repo)
        # 2020 drought should appear before 1998 flood
        pos_2020 = result.find("2020")
        pos_1998 = result.find("1998")
        if pos_2020 > 0 and pos_1998 > 0:
            assert pos_2020 < pos_1998
