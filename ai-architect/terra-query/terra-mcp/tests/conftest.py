"""Shared pytest fixtures for terra-mcp tests."""
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pandas as pd
import pytest

# Ensure terra-mcp root is on path
sys.path.insert(0, str(Path(__file__).parent.parent))

from data.loaders.base_loader import NORMALIZED_SCHEMA


@pytest.fixture()
def sample_df() -> pd.DataFrame:
    """Small disaster DataFrame with realistic values for unit testing."""
    rows = [
        {
            "event_id": "eosdis_0",
            "disaster_type": "flood",
            "subtype": "riverine flood",
            "country": "Bangladesh",
            "country_iso3": "BGD",
            "region": "Southern Asia",
            "start_date": pd.Timestamp("1998-07-01"),
            "end_date": pd.Timestamp("1998-09-30"),
            "deaths": 1050.0,
            "injured": pd.NA,
            "affected": 30_000_000.0,
            "economic_damage_usd": 5_900_000_000.0,
            "magnitude": pd.NA,
            "source": "eosdis",
            "source_event_id": "1998-0001-BGD",
        },
        {
            "event_id": "eosdis_1",
            "disaster_type": "earthquake",
            "subtype": "ground movement",
            "country": "Indonesia",
            "country_iso3": "IDN",
            "region": "South-Eastern Asia",
            "start_date": pd.Timestamp("2004-12-26"),
            "end_date": pd.Timestamp("2004-12-26"),
            "deaths": 165708.0,
            "injured": 127774.0,
            "affected": 500_000.0,
            "economic_damage_usd": 10_000_000_000.0,
            "magnitude": 9.1,
            "source": "eosdis",
            "source_event_id": "2004-0001-IDN",
        },
        {
            "event_id": "noaa_0",
            "disaster_type": "storm",
            "subtype": None,
            "country": "United States",
            "country_iso3": "USA",
            "region": "Louisiana",
            "start_date": pd.Timestamp("2005-08-29"),
            "end_date": pd.Timestamp("2005-09-05"),
            "deaths": 1833.0,
            "injured": pd.NA,
            "affected": 1_000_000.0,
            "economic_damage_usd": 125_000_000_000.0,
            "magnitude": pd.NA,
            "source": "noaa",
            "source_event_id": "10001011",
        },
        {
            "event_id": "eosdis_3",
            "disaster_type": "earthquake",
            "subtype": "ground movement",
            "country": "Haiti",
            "country_iso3": "HTI",
            "region": "Caribbean",
            "start_date": pd.Timestamp("2010-01-12"),
            "end_date": pd.Timestamp("2010-01-12"),
            "deaths": 222570.0,
            "injured": 300000.0,
            "affected": 3_700_000.0,
            "economic_damage_usd": 8_000_000_000.0,
            "magnitude": 7.0,
            "source": "eosdis",
            "source_event_id": "2010-0001-HTI",
        },
        {
            "event_id": "eosdis_4",
            "disaster_type": "drought",
            "subtype": None,
            "country": "Zimbabwe",
            "country_iso3": "ZWE",
            "region": "Eastern Africa",
            "start_date": pd.Timestamp("2020-01-01"),
            "end_date": pd.Timestamp("2020-12-31"),
            "deaths": pd.NA,
            "injured": pd.NA,
            "affected": 5_700_000.0,
            "economic_damage_usd": pd.NA,
            "magnitude": pd.NA,
            "source": "eosdis",
            "source_event_id": "2020-0001-ZWE",
        },
    ]
    return pd.DataFrame(rows, columns=NORMALIZED_SCHEMA)


@pytest.fixture()
def mock_repo(sample_df):
    """Mock DisasterRepository backed by sample_df."""
    from unittest.mock import MagicMock
    repo = MagicMock()
    repo.df = sample_df

    def _query(disaster_type=None, country=None, year_from=None, year_to=None):
        result = sample_df.copy()
        if disaster_type:
            result = result[result["disaster_type"] == disaster_type.lower()]
        if country:
            c = country.lower()
            mask = (
                result["country"].str.lower().str.contains(c, na=False)
                | result["country_iso3"].str.lower().str.contains(c, na=False)
            )
            result = result[mask]
        if year_from is not None:
            result = result[result["start_date"].dt.year >= year_from]
        if year_to is not None:
            result = result[result["start_date"].dt.year <= year_to]
        return result

    repo.query.side_effect = _query
    return repo


@pytest.fixture()
def mock_eonet_client():
    """Mock EonetClient that returns a fixed list of events."""
    client = MagicMock()
    client.get_active_events_sync.return_value = [
        {
            "event_id": "EONET_5765",
            "title": "Mauna Loa Volcano, Hawaii",
            "disaster_type": "volcano",
            "status": "open",
            "date": "2023-01-01T00:00:00Z",
            "coordinates": [-155.6, 19.5],
            "source_url": "https://volcanoes.usgs.gov/",
            "eonet_link": "https://eonet.gsfc.nasa.gov/api/v3/events/EONET_5765",
        },
        {
            "event_id": "EONET_5766",
            "title": "California Wildfires",
            "disaster_type": "wildfire",
            "status": "open",
            "date": "2023-01-05T00:00:00Z",
            "coordinates": [-120.5, 37.3],
            "source_url": "https://inciweb.nwcg.gov/",
            "eonet_link": "https://eonet.gsfc.nasa.gov/api/v3/events/EONET_5766",
        },
    ]
    return client
