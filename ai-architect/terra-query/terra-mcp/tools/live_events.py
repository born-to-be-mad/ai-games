"""MCP tool: get_live_events — real-time events from NASA EONET."""

import logging
from typing import TYPE_CHECKING, Annotated, Optional

if TYPE_CHECKING:
    from data.loaders.eonet_client import EonetClient

logger = logging.getLogger(__name__)


def get_live_events_logic(
    eonet_client: "EonetClient",
    disaster_type: Optional[str] = None,
) -> str:
    """Core logic for get_live_events — testable without FastMCP."""
    events = eonet_client.get_active_events_sync(disaster_type=disaster_type, days=30)

    if not events:
        scope = f" of type '{disaster_type}'" if disaster_type else ""
        return (
            f"No active natural disaster events{scope} found in the last 30 days, "
            "or NASA EONET API is currently unavailable."
        )

    scope = f" ({disaster_type})" if disaster_type else ""
    lines = [f"Active natural disaster events{scope} (last 30 days via NASA EONET):\n"]

    for i, event in enumerate(events, 1):
        title = event.get("title", "Untitled event")
        dtype = str(event.get("disaster_type") or "unknown").title()
        status = event.get("status", "open")
        date = event.get("date", "unknown date")
        coords = event.get("coordinates")
        url = event.get("source_url") or event.get("eonet_link", "")

        coord_str = ""
        if coords and isinstance(coords, list) and len(coords) >= 2:
            coord_str = f" at [{coords[1]:.2f}°N, {coords[0]:.2f}°E]"

        lines.append(f"  {i}. {title}")
        lines.append(f"     Type: {dtype} | Status: {status} | Date: {date}{coord_str}")
        if url:
            lines.append(f"     More info: {url}")

    return "\n".join(lines)


def register_live_events_tool(mcp, eonet_client: "EonetClient") -> None:
    """Register the get_live_events tool on the FastMCP instance."""

    @mcp.tool()
    def get_live_events(
        disaster_type: Annotated[
            Optional[str],
            "Filter live events by type. "
            "NASA EONET categories: 'wildfires', 'severeStorms', 'volcanoes', "
            "'earthquakes', 'floods', 'drought'. Leave None for all active events.",
        ] = None,
    ) -> str:
        """
        Fetches currently active natural disaster events from NASA's Earth Observatory
        Natural Event Tracker (EONET). Returns events happening right now or in the
        last 30 days with their geographic coordinates and severity if available.
        Use when the user asks about 'current', 'ongoing', 'right now', or 'latest' events.
        Falls back gracefully if EONET API is unavailable.
        """
        return get_live_events_logic(eonet_client, disaster_type)
