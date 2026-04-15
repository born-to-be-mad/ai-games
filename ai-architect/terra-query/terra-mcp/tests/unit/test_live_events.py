"""Unit tests for get_live_events tool logic."""

from tools.live_events import get_live_events_logic


class TestGetLiveEvents:
    def test_returns_events(self, mock_eonet_client):
        result = get_live_events_logic(mock_eonet_client)
        assert "Mauna Loa" in result or "volcano" in result.lower()
        assert "California" in result or "wildfire" in result.lower()

    def test_filter_by_type(self, mock_eonet_client):
        mock_eonet_client.get_active_events_sync.return_value = [
            {
                "event_id": "EONET_5765",
                "title": "Mauna Loa Volcano, Hawaii",
                "disaster_type": "volcano",
                "status": "open",
                "date": "2023-01-01T00:00:00Z",
                "coordinates": [-155.6, 19.5],
                "source_url": "",
                "eonet_link": "",
            }
        ]
        result = get_live_events_logic(mock_eonet_client, disaster_type="volcano")
        assert "Mauna Loa" in result or "Volcano" in result

    def test_no_events_graceful(self, mock_eonet_client):
        mock_eonet_client.get_active_events_sync.return_value = []
        result = get_live_events_logic(mock_eonet_client)
        assert (
            "no active" in result.lower()
            or "not found" in result.lower()
            or "unavailable" in result.lower()
        )

    def test_includes_coordinates(self, mock_eonet_client):
        result = get_live_events_logic(mock_eonet_client)
        assert "°N" in result or "°E" in result or "at [" in result

    def test_includes_date(self, mock_eonet_client):
        result = get_live_events_logic(mock_eonet_client)
        assert "2023" in result or "Date:" in result

    def test_type_filter_passed_to_client(self, mock_eonet_client):
        get_live_events_logic(mock_eonet_client, disaster_type="wildfire")
        mock_eonet_client.get_active_events_sync.assert_called_with(
            disaster_type="wildfire", days=30
        )
