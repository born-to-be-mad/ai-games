"""MCP tools: statistics, deadliest events, trends, country comparison."""

import logging
from typing import TYPE_CHECKING, Annotated, Optional

import pandas as pd

from validation.tool_params import (
    CompareCountriesParams,
    DisasterStatsParams,
    TrendParams,
)

if TYPE_CHECKING:
    from data.repository import DisasterRepository

logger = logging.getLogger(__name__)


# -------------------------------------------------------------------------
# Logic functions (testable without FastMCP)
# -------------------------------------------------------------------------

def get_disaster_statistics_logic(
    repo: "DisasterRepository",
    disaster_type: Optional[str] = None,
    country: Optional[str] = None,
    year_from: Optional[int] = None,
    year_to: Optional[int] = None,
) -> str:
    try:
        params = DisasterStatsParams(
            disaster_type=disaster_type, country=country,
            year_from=year_from, year_to=year_to,
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
        return "No records found for the given filters."

    total_events = len(df)
    total_deaths = _safe_sum(df, "deaths")
    total_affected = _safe_sum(df, "affected")
    total_damage = _safe_sum(df, "economic_damage_usd")
    avg_deaths = (total_deaths / total_events) if total_events > 0 and total_deaths else None

    worst_idx = df["deaths"].idxmax() if not df["deaths"].isna().all() else None
    worst_event = _describe_event(df.loc[worst_idx]) if worst_idx is not None else "N/A"

    type_counts = df["disaster_type"].value_counts()
    most_common = type_counts.index[0].title() if not type_counts.empty else "N/A"

    lines = [
        "=== Disaster Statistics ===",
        f"Total events:       {total_events:,}",
        f"Total deaths:       {_fmt_int(total_deaths)}",
        f"Total affected:     {_fmt_int(total_affected)}",
        f"Total econ. damage: {_fmt_damage(total_damage)}",
    ]
    if avg_deaths is not None:
        lines.append(f"Avg deaths/event:   {avg_deaths:.1f}")
    lines.append(f"Worst single event: {worst_event}")
    lines.append(f"Most frequent type: {most_common}")
    return "\n".join(lines)


def get_deadliest_disasters_logic(
    repo: "DisasterRepository",
    n: int = 10,
    disaster_type: Optional[str] = None,
    year_from: Optional[int] = None,
    year_to: Optional[int] = None,
) -> str:
    n = max(1, min(n, 50))
    try:
        from validation.tool_params import DisasterQueryParams
        params = DisasterQueryParams(
            disaster_type=disaster_type,
            year_from=year_from,
            year_to=year_to,
            limit=n,
        )
    except Exception as exc:
        return f"Invalid parameters: {exc}"

    df = repo.query(
        disaster_type=params.disaster_type,
        year_from=params.year_from,
        year_to=params.year_to,
    )

    if df.empty:
        return "No records found."

    df_sorted = df.dropna(subset=["deaths"]).sort_values("deaths", ascending=False).head(n)
    if df_sorted.empty:
        return "No records with known death tolls found."

    lines = [f"Top {len(df_sorted)} deadliest disasters:\n"]
    for rank, (_, row) in enumerate(df_sorted.iterrows(), 1):
        deaths = int(row["deaths"]) if not pd.isna(row["deaths"]) else 0
        country = str(row.get("country") or row.get("country_iso3") or "Unknown")
        dtype = str(row.get("disaster_type") or "Unknown").title()
        year = _get_year(row.get("start_date"))
        lines.append(f"  {rank}. {dtype} in {country} ({year}) — {deaths:,} deaths")
    return "\n".join(lines)


def get_disaster_trends_logic(
    repo: "DisasterRepository",
    disaster_type: str,
    country: Optional[str] = None,
    year_from: int = 1970,
    year_to: int = 2021,
) -> str:
    try:
        params = TrendParams(
            disaster_type=disaster_type, country=country,
            year_from=year_from, year_to=year_to,
        )
    except Exception as exc:
        return f"Invalid parameters: {exc}"

    df = repo.query(
        disaster_type=params.disaster_type,
        country=params.country,
        year_from=params.year_from,
        year_to=params.year_to,
    )

    if df.empty or "start_date" not in df.columns:
        return f"No trend data found for {params.disaster_type}."

    df = df.dropna(subset=["start_date"]).copy()
    df["year"] = df["start_date"].dt.year

    yearly = (
        df.groupby("year")
        .agg(events=("event_id", "count"), deaths=("deaths", "sum"), affected=("affected", "sum"))
        .reindex(range(params.year_from, params.year_to + 1), fill_value=0)
    )

    scope = f" in {params.country}" if params.country else " (global)"
    lines = [
        f"Year-by-year {params.disaster_type} trend{scope} ({params.year_from}–{params.year_to}):\n",
        f"{'Year':<6} {'Events':>8} {'Deaths':>12} {'Affected':>14}",
        "-" * 44,
    ]
    for year, row in yearly.iterrows():
        lines.append(
            f"{year:<6} {int(row['events']):>8,} "
            f"{_fmt_int(row['deaths']):>12} "
            f"{_fmt_int(row['affected']):>14}"
        )

    mid = (params.year_from + params.year_to) // 2
    first_half = yearly.loc[params.year_from:mid, "events"].mean()
    second_half = yearly.loc[mid + 1:params.year_to, "events"].mean()
    if first_half > 0:
        change_pct = (second_half - first_half) / first_half * 100
        direction = "increased" if change_pct > 5 else "decreased" if change_pct < -5 else "remained stable"
        lines.append(f"\nTrend: {params.disaster_type} frequency has {direction} "
                     f"({change_pct:+.0f}% from first half to second half of period).")
    return "\n".join(lines)


def compare_disasters_across_countries_logic(
    repo: "DisasterRepository",
    disaster_type: str,
    countries: list,
    year_from: Optional[int] = None,
    year_to: Optional[int] = None,
) -> str:
    try:
        params = CompareCountriesParams(
            disaster_type=disaster_type, countries=countries,
            year_from=year_from, year_to=year_to,
        )
    except Exception as exc:
        return f"Invalid parameters: {exc}"

    lines = [
        f"Comparison of {params.disaster_type} impact across countries "
        f"({params.year_from or 'all'}–{params.year_to or 'present'}):\n",
        f"{'Country':<20} {'Events':>8} {'Deaths':>12} {'Affected':>14} {'Damage':>16}",
        "-" * 72,
    ]

    for country in params.countries:
        df = repo.query(
            disaster_type=params.disaster_type,
            country=country,
            year_from=params.year_from,
            year_to=params.year_to,
        )
        events = len(df)
        deaths = _safe_sum(df, "deaths")
        affected = _safe_sum(df, "affected")
        damage = _safe_sum(df, "economic_damage_usd")
        lines.append(
            f"{country:<20} {events:>8,} "
            f"{_fmt_int(deaths):>12} "
            f"{_fmt_int(affected):>14} "
            f"{_fmt_damage(damage):>16}"
        )

    return "\n".join(lines)


# -------------------------------------------------------------------------
# FastMCP registration
# -------------------------------------------------------------------------

def register_stats_tools(mcp, repo: "DisasterRepository") -> None:
    """Register all statistics-related tools on the FastMCP instance."""

    @mcp.tool()
    def get_disaster_statistics(
        disaster_type: Annotated[
            Optional[str],
            "Type of natural disaster to filter by. "
            "Accepted values: 'flood', 'earthquake', 'storm', 'drought', 'wildfire', "
            "'volcano', 'landslide', 'epidemic', 'extreme_temperature'. Leave None for all types.",
        ] = None,
        country: Annotated[Optional[str], "ISO 3166-1 alpha-3 country code or full country name."] = None,
        year_from: Annotated[Optional[int], "Start year (inclusive)."] = None,
        year_to: Annotated[Optional[int], "End year (inclusive)."] = None,
    ) -> str:
        """
        Compute aggregate statistics for natural disaster events matching the given filters.
        Returns: total event count, total deaths, total affected persons, total economic damage (USD),
        average deaths per event, worst single event, most frequent disaster type.
        Use this tool when the user asks about scale, totals, averages, or comparative impact.
        """
        return get_disaster_statistics_logic(repo, disaster_type, country, year_from, year_to)

    @mcp.tool()
    def get_deadliest_disasters(
        n: Annotated[int, "Number of top deadliest events to return (1–50)."] = 10,
        disaster_type: Annotated[Optional[str], "Optional filter by disaster type."] = None,
        year_from: Annotated[Optional[int], "Start year (inclusive)."] = None,
        year_to: Annotated[Optional[int], "End year (inclusive)."] = None,
    ) -> str:
        """
        Returns the N deadliest natural disaster events ordered by confirmed death toll (descending).
        Useful for answering 'what was the worst earthquake ever?' or
        'top 5 deadliest floods in Asia' type questions.
        """
        return get_deadliest_disasters_logic(repo, n, disaster_type, year_from, year_to)

    @mcp.tool()
    def get_disaster_trends(
        disaster_type: Annotated[
            str,
            "Type of disaster to analyze trend for. "
            "Valid: 'flood', 'earthquake', 'storm', 'drought', 'wildfire', "
            "'volcano', 'landslide', 'epidemic', 'extreme_temperature'.",
        ],
        country: Annotated[Optional[str], "Country to scope the trend. Leave None for global."] = None,
        year_from: Annotated[int, "Start year for trend window."] = 1970,
        year_to: Annotated[int, "End year for trend window."] = 2021,
    ) -> str:
        """
        Returns year-by-year event counts, death tolls, and affected population for a
        given disaster type. Use this when the user asks 'are floods increasing?',
        'how has the frequency of hurricanes changed?', or any question involving trends over time.
        """
        return get_disaster_trends_logic(repo, disaster_type, country, year_from, year_to)

    @mcp.tool()
    def compare_disasters_across_countries(
        disaster_type: Annotated[str, "Type of disaster for comparison."],
        countries: Annotated[list, "List of 2–5 country codes or names to compare."],
        year_from: Annotated[Optional[int], "Start year (inclusive)."] = None,
        year_to: Annotated[Optional[int], "End year (inclusive)."] = None,
    ) -> str:
        """
        Side-by-side comparison of disaster impact across multiple countries.
        Returns a table with per-country totals: events, deaths, affected, economic damage.
        Use when the user asks to compare how different countries are affected by a specific disaster type.
        """
        return compare_disasters_across_countries_logic(repo, disaster_type, countries, year_from, year_to)


# -------------------------------------------------------------------------
# helpers
# -------------------------------------------------------------------------

def _safe_sum(df: pd.DataFrame, col: str):
    if col not in df.columns or df[col].isna().all():
        return None
    return df[col].sum(skipna=True)


def _fmt_int(val) -> str:
    if val is None or (hasattr(val, "__float__") and pd.isna(val)):
        return "N/A"
    try:
        n = float(val)
        if n >= 1_000_000:
            return f"{n / 1_000_000:.1f}M"
        if n >= 1_000:
            return f"{n / 1_000:.1f}K"
        return f"{int(n):,}"
    except (TypeError, ValueError):
        return "N/A"


def _fmt_damage(val) -> str:
    if val is None or (hasattr(val, "__float__") and pd.isna(val)):
        return "N/A"
    try:
        n = float(val)
        if n >= 1e9:
            return f"${n / 1e9:.1f}B"
        if n >= 1e6:
            return f"${n / 1e6:.1f}M"
        if n >= 1e3:
            return f"${n / 1e3:.1f}K"
        return f"${n:.0f}"
    except (TypeError, ValueError):
        return "N/A"


def _describe_event(row: pd.Series) -> str:
    dtype = str(row.get("disaster_type") or "Unknown").title()
    country = str(row.get("country") or "Unknown")
    year = _get_year(row.get("start_date"))
    deaths = int(row["deaths"]) if not pd.isna(row.get("deaths")) else 0
    return f"{dtype} in {country} ({year}): {deaths:,} deaths"


def _get_year(val) -> str:
    if val is None or pd.isna(val):
        return "unknown"
    try:
        return str(pd.Timestamp(val).year)
    except Exception:
        return str(val)[:4]
