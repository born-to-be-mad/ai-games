"""DataNormalizer: country names → ISO 3166-1 alpha-3, null handling, date normalization."""
import logging
from functools import lru_cache

import pandas as pd

logger = logging.getLogger(__name__)

# Manual overrides for non-standard country names not covered by pycountry fuzzy match
_COUNTRY_OVERRIDES: dict[str, str] = {
    "burma": "MMR",
    "myanmar": "MMR",
    "czech republic": "CZE",
    "czechia": "CZE",
    "democratic republic of congo": "COD",
    "dr congo": "COD",
    "republic of congo": "COG",
    "ivory coast": "CIV",
    "cote d'ivoire": "CIV",
    "south korea": "KOR",
    "north korea": "PRK",
    "taiwan": "TWN",
    "bolivia": "BOL",
    "iran": "IRN",
    "laos": "LAO",
    "moldova": "MDA",
    "russia": "RUS",
    "russian federation": "RUS",
    "syria": "SYR",
    "tanzania": "TZA",
    "united states": "USA",
    "usa": "USA",
    "united states of america": "USA",
    "uk": "GBR",
    "united kingdom": "GBR",
    "vietnam": "VNM",
    "viet nam": "VNM",
    "venezuela": "VEN",
    "west germany": "DEU",
    "east germany": "DEU",
    "yugoslavia": "SRB",
    "soviet union": "RUS",
    "ussr": "RUS",
    "micronesia": "FSM",
    "holy see": "VAT",
}


class DataNormalizer:
    """
    Normalizes disaster DataFrames:
    - Deaths=0 means zero confirmed deaths (kept as 0)
    - Deaths=NaN means unknown/not reported (kept as NaN — not converted to 0)
    - All country names → ISO 3166-1 alpha-3
    - start_date / end_date → pandas Timestamp
    """

    def normalize(self, df: pd.DataFrame) -> pd.DataFrame:
        df = df.copy()
        df = self._normalize_numeric_fields(df)
        df = self._normalize_dates(df)
        if "country" in df.columns and "country_iso3" not in df.columns:
            df["country_iso3"] = df["country"].apply(self.country_to_iso3)
        return df

    def _normalize_numeric_fields(self, df: pd.DataFrame) -> pd.DataFrame:
        for col in ("deaths", "injured", "affected", "economic_damage_usd", "magnitude"):
            if col in df.columns:
                # Preserve NaN vs 0 distinction — don't fillna(0)
                df[col] = pd.to_numeric(df[col], errors="coerce")
        return df

    def _normalize_dates(self, df: pd.DataFrame) -> pd.DataFrame:
        for col in ("start_date", "end_date"):
            if col in df.columns:
                df[col] = pd.to_datetime(df[col], errors="coerce", utc=False)
        return df

    def country_to_iso3(self, country: str | None) -> str | None:
        if country is None:
            return None
        try:
            if pd.isna(country):
                return None
        except (TypeError, ValueError):
            pass
        if not country:
            return None
        normalized = str(country).strip().lower()
        if normalized in _COUNTRY_OVERRIDES:
            return _COUNTRY_OVERRIDES[normalized]
        return self._pycountry_lookup(country)

    @staticmethod
    @lru_cache(maxsize=512)
    def _pycountry_lookup(country: str) -> str | None:
        try:
            import pycountry
            result = pycountry.countries.get(name=country)
            if result:
                return result.alpha_3
            # fuzzy search
            results = pycountry.countries.search_fuzzy(country)
            if results:
                return results[0].alpha_3
        except (ImportError, LookupError):
            pass
        return None
