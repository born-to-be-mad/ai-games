"""Unit tests for disaster_stats tool logic."""

from tools.disaster_stats import (
    compare_disasters_across_countries_logic,
    get_deadliest_disasters_logic,
    get_disaster_statistics_logic,
    get_disaster_trends_logic,
)


class TestGetDisasterStatistics:
    def test_returns_totals(self, mock_repo):
        result = get_disaster_statistics_logic(mock_repo)
        assert "Total events" in result

    def test_filter_by_type(self, mock_repo):
        result = get_disaster_statistics_logic(mock_repo, disaster_type="earthquake")
        assert "Statistics" in result

    def test_no_data_graceful(self, mock_repo):
        result = get_disaster_statistics_logic(mock_repo, disaster_type="epidemic")
        assert "no records" in result.lower()

    def test_invalid_type_returns_error(self, mock_repo):
        result = get_disaster_statistics_logic(mock_repo, disaster_type="unknown_type")
        assert "invalid" in result.lower() or "unknown" in result.lower()

    def test_shows_worst_event(self, mock_repo):
        result = get_disaster_statistics_logic(mock_repo)
        assert "Worst single event" in result

    def test_shows_most_frequent_type(self, mock_repo):
        result = get_disaster_statistics_logic(mock_repo)
        assert "Most frequent type" in result


class TestGetDeadliestDisasters:
    def test_returns_ranked_list(self, mock_repo):
        result = get_deadliest_disasters_logic(mock_repo, n=3)
        assert "deadliest" in result.lower()
        assert "1." in result

    def test_n_clamped_to_1(self, mock_repo):
        result = get_deadliest_disasters_logic(mock_repo, n=0)
        assert "1 deadliest" in result.lower() or "Top 1" in result

    def test_filter_by_type(self, mock_repo):
        result = get_deadliest_disasters_logic(mock_repo, disaster_type="earthquake")
        assert "Earthquake" in result

    def test_death_toll_descending(self, mock_repo):
        result = get_deadliest_disasters_logic(mock_repo, n=5)
        # Haiti (222570) should be above Indonesia (165708)
        haiti_pos = result.find("Haiti")
        indonesia_pos = result.find("Indonesia")
        if haiti_pos > 0 and indonesia_pos > 0:
            assert haiti_pos < indonesia_pos


class TestGetDisasterTrends:
    def test_returns_year_table(self, mock_repo):
        result = get_disaster_trends_logic(
            mock_repo, disaster_type="flood", year_from=1990, year_to=2021
        )
        assert "Year" in result
        assert "Events" in result

    def test_includes_trend_summary(self, mock_repo):
        result = get_disaster_trends_logic(
            mock_repo, disaster_type="earthquake", year_from=2000, year_to=2021
        )
        assert "Trend" in result

    def test_invalid_type_returns_error(self, mock_repo):
        result = get_disaster_trends_logic(mock_repo, disaster_type="tornado")
        assert "invalid" in result.lower() or "unknown" in result.lower()

    def test_empty_data_graceful(self, mock_repo):
        result = get_disaster_trends_logic(mock_repo, disaster_type="epidemic")
        assert "no trend" in result.lower() or "not found" in result.lower()


class TestCompareDisastersAcrossCountries:
    def test_returns_comparison_table(self, mock_repo):
        result = compare_disasters_across_countries_logic(
            mock_repo,
            disaster_type="earthquake",
            countries=["Indonesia", "Japan", "Haiti"],
        )
        assert "Indonesia" in result

    def test_too_few_countries_error(self, mock_repo):
        result = compare_disasters_across_countries_logic(
            mock_repo, disaster_type="flood", countries=["Bangladesh"]
        )
        assert "invalid" in result.lower()

    def test_column_headers_present(self, mock_repo):
        result = compare_disasters_across_countries_logic(
            mock_repo, disaster_type="flood", countries=["Bangladesh", "India"]
        )
        assert "Events" in result
        assert "Deaths" in result
