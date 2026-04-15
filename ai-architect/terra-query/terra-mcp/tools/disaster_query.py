"""MCP tool: query_disasters — structured search over the disaster database."""

import logging
from typing import TYPE_CHECKING, Annotated, Optional

import pandas as pd

from validation.tool_params import DisasterQueryParams

if TYPE_CHECKING:
    from data.repository import DisasterRepository

logger = logging.getLogger(__name__)


def query_disasters_logic(
    repo: "DisasterRepository",
    disaster_type: Optional[str] = None,
    country: Optional[str] = None,
    year_from: Optional[int] = None,
    year_to: Optional[int] = None,
    limit: int = 20,
) -> str:
    """Core logic for query_disasters — testable without FastMCP."""
    try:
        params = DisasterQueryParams(
            disaster_type=disaster_type,
            country=country,
            year_from=year_from,
            year_to=year_to,
            limit=limit,
        )
    except Exception as exc:
        return f"Invalid parameters: {exc}"

    df = repo.query(
        disaster_type=params.disaster_type,
        country=params.country,
        year_from=params.year_from,
        year_to=params.year_to,
    )

    if df.empty:
        filters = _describe_filters(params)
        return f"No disaster records found{filters}."

    df = df.sort_values("start_date", ascending=False, na_position="last")
    df = df.head(params.limit)
    return _format_results(df, params)


def register_query_tool(mcp, repo: "DisasterRepository") -> None:
    """Register the query_disasters tool on the FastMCP instance."""

    @mcp.tool()
    def query_disasters(
        disaster_type: Annotated[
            Optional[str],
            "Type of natural disaster to filter by. "
            "Accepted values: 'flood', 'earthquake', 'storm', 'drought', 'wildfire', "
            "'volcano', 'landslide', 'epidemic', 'extreme_temperature'. "
            "Leave None to search all types.",
        ] = None,
        country: Annotated[
            Optional[str],
            "ISO 3166-1 alpha-3 country code (e.g. 'BGD' for Bangladesh, 'USA', 'JPN') "
            "or full country name. Leave None for all countries.",
        ] = None,
        year_from: Annotated[
            Optional[int],
            "Start year (inclusive) for the search range. Earliest available: 1900.",
        ] = None,
        year_to: Annotated[
            Optional[int],
            "End year (inclusive). Defaults to latest available year.",
        ] = None,
        limit: Annotated[
            int,
            "Maximum number of records to return. Default 20, max 100.",
        ] = 20,
    ) -> str:
        """
        Search the natural disaster database for events matching the given criteria.
        Returns a structured list of disaster events including dates, location, death toll,
        number of people affected, and estimated economic damage. Use this tool when the
        user asks about specific disaster events or wants to explore what disasters
        occurred in a particular region or time period.
        """
        return query_disasters_logic(
            repo, disaster_type, country, year_from, year_to, limit
        )


def _describe_filters(params: DisasterQueryParams) -> str:
    parts = []
    if params.disaster_type:
        parts.append(f"type={params.disaster_type}")
    if params.country:
        parts.append(f"country={params.country}")
    if params.year_from:
        parts.append(f"from {params.year_from}")
    if params.year_to:
        parts.append(f"to {params.year_to}")
    return f" matching {', '.join(parts)}" if parts else ""


def _format_results(df: pd.DataFrame, params: DisasterQueryParams) -> str:
    lines = [
        f"Found {len(df)} disaster record(s)"
        + _describe_filters(params)
        + " (sorted by most recent):\n"
    ]
    for _, row in df.iterrows():
        lines.append(_format_row(row))
    return "\n".join(lines)


def _format_row(row: pd.Series) -> str:
    dtype = str(row.get("disaster_type") or "Unknown").title()
    country = str(row.get("country") or row.get("country_iso3") or "Unknown")
    start = _fmt_date(row.get("start_date"))
    end = _fmt_date(row.get("end_date"))
    date_str = f"{start}–{end}" if end and end != start else start

    deaths = _fmt_val(row.get("deaths"))
    affected = _fmt_val(row.get("affected"))
    damage = _fmt_damage(row.get("economic_damage_usd"))
    source = str(row.get("source") or "")

    parts = [f"• {dtype} in {country} ({date_str})"]
    if deaths:
        parts.append(f"  Deaths: {deaths}")
    if affected:
        parts.append(f"  Affected: {affected}")
    if damage:
        parts.append(f"  Damage: {damage}")
    if source:
        parts.append(f"  Source: {source}")
    return "\n".join(parts)


def _fmt_date(val) -> str:
    if val is None or pd.isna(val):
        return "unknown"
    try:
        return pd.Timestamp(val).strftime("%Y-%m-%d")
    except Exception:
        return str(val)[:10]


def _fmt_val(val) -> str:
    if val is None or pd.isna(val):
        return ""
    try:
        n = float(val)
        if n >= 1_000_000:
            return f"{n / 1_000_000:.1f}M"
        if n >= 1_000:
            return f"{n / 1_000:.1f}K"
        return f"{int(n):,}"
    except (TypeError, ValueError):
        return str(val)


def _fmt_damage(val) -> str:
    if val is None or pd.isna(val):
        return ""
    try:
        n = float(val)
        if n >= 1e9:
            return f"${n / 1e9:.1f}B USD"
        if n >= 1e6:
            return f"${n / 1e6:.1f}M USD"
        if n >= 1e3:
            return f"${n / 1e3:.1f}K USD"
        return f"${n:.0f} USD"
    except (TypeError, ValueError):
        return str(val)
