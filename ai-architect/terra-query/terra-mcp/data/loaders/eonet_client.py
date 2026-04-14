"""Async HTTP client for NASA EONET (Earth Observatory Natural Event Tracker)."""
import asyncio
import logging
from typing import Any

import httpx

from resilience.circuit_breaker import CircuitBreaker, CircuitBreakerOpenError
from resilience.retry_decorator import retry_on_network_error

logger = logging.getLogger(__name__)

EONET_BASE_URL = "https://eonet.gsfc.nasa.gov/api/v3"

# EONET category → normalized disaster type
_CATEGORY_MAP = {
    "wildfires": "wildfire",
    "severeStorms": "storm",
    "volcanoes": "volcano",
    "earthquakes": "earthquake",
    "floods": "flood",
    "drought": "drought",
    "dustHaze": "storm",
    "manmade": None,        # skip man-made events
    "seaLakeIce": "extreme_temperature",
    "tempExtremes": "extreme_temperature",
    "snow": "storm",
    "waterColor": None,     # skip
}


class EonetClient:
    """
    Fetches currently active natural disaster events from NASA EONET API v3.

    Resilience:
      - Circuit breaker (5-failure threshold, 60s reset): isolates EONET outages
        so the rest of the server stays healthy.
      - Retry (3 attempts, exponential backoff): handles transient network blips.
    """

    # Module-level circuit breaker — shared across all EonetClient instances
    _circuit_breaker = CircuitBreaker(
        name="eonet",
        failure_threshold=5,
        reset_timeout_s=60.0,
        success_threshold=2,
    )

    def __init__(self, timeout: float = 15.0) -> None:
        self._timeout = timeout

    @retry_on_network_error(max_attempts=3, wait_seconds=1.0)
    async def get_active_events(
        self,
        disaster_type: str | None = None,
        days: int = 30,
    ) -> list[dict[str, Any]]:
        """
        Return active events from the past `days` days.
        Filters by normalized disaster_type if provided.
        Returns empty list on API error.
        """
        params: dict[str, Any] = {
            "status": "open",
            "limit": 100,
            "days": days,
        }

        # Resolve disaster_type → EONET category id(s)
        category_ids = self._disaster_type_to_categories(disaster_type)
        if disaster_type is not None and not category_ids:
            return []  # unknown type, return empty rather than error

        if category_ids:
            params["category"] = ",".join(category_ids)

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.get(f"{EONET_BASE_URL}/events", params=params)
                resp.raise_for_status()
                data = resp.json()
        except httpx.TimeoutException:
            logger.warning("EONET API timed out after %.1fs", self._timeout)
            return []
        except httpx.HTTPStatusError as exc:
            logger.warning("EONET API returned %s", exc.response.status_code)
            return []
        except CircuitBreakerOpenError as exc:
            logger.warning("EONET circuit breaker open: %s", exc)
            return []
        except Exception as exc:
            logger.warning("EONET API error: %s", exc)
            return []

        events = data.get("events", [])
        return [self._normalize_event(e) for e in events if self._normalize_event(e)]

    def get_active_events_sync(
        self, disaster_type: str | None = None, days: int = 30
    ) -> list[dict[str, Any]]:
        """
        Synchronous wrapper for use in non-async contexts (FastMCP tools).
        Delegates to the circuit breaker so CB state is shared with async callers.
        """
        async def _inner():
            return await self.get_active_events(disaster_type, days)

        try:
            return self._circuit_breaker.call(
                lambda: asyncio.get_event_loop().run_until_complete(_inner())
                if not asyncio.get_event_loop().is_running()
                else _run_in_thread(_inner, timeout=self._timeout + 5)
            )
        except CircuitBreakerOpenError as exc:
            logger.warning("EONET circuit breaker open (sync): %s", exc)
            return []
        except Exception as exc:
            logger.warning("EONET sync wrapper error: %s", exc)
            return []


def _run_in_thread(coro_fn, timeout: float) -> list:
    import concurrent.futures
    with concurrent.futures.ThreadPoolExecutor() as pool:
        future = pool.submit(asyncio.run, coro_fn())
        return future.result(timeout=timeout)

    def _normalize_event(self, raw: dict) -> dict | None:
        categories = raw.get("categories", [])
        disaster_type = None
        for cat in categories:
            mapped = _CATEGORY_MAP.get(cat.get("id", ""))
            if mapped:
                disaster_type = mapped
                break
        if disaster_type is None:
            return None

        geometry = raw.get("geometry", [])
        coords = None
        date_str = None
        if geometry:
            last = geometry[-1]
            coords = last.get("coordinates")
            date_str = last.get("date", "")

        return {
            "event_id": raw.get("id", ""),
            "title": raw.get("title", ""),
            "disaster_type": disaster_type,
            "status": raw.get("status", "open"),
            "date": date_str,
            "coordinates": coords,
            "source_url": (raw.get("sources") or [{}])[0].get("url", ""),
            "eonet_link": raw.get("link", ""),
        }

    @staticmethod
    def _disaster_type_to_categories(disaster_type: str | None) -> list[str]:
        if disaster_type is None:
            return []
        dt = disaster_type.lower()
        mapping: dict[str, list[str]] = {
            "wildfire": ["wildfires"],
            "storm": ["severeStorms"],
            "volcano": ["volcanoes"],
            "earthquake": ["earthquakes"],
            "flood": ["floods"],
            "drought": ["drought"],
            "extreme_temperature": ["tempExtremes", "seaLakeIce"],
        }
        return mapping.get(dt, [])
