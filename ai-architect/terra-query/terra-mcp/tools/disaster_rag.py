"""MCP tool: search_disasters_semantic — hybrid BM25 + FAISS semantic search."""

import logging
from typing import TYPE_CHECKING, Annotated, Optional

if TYPE_CHECKING:
    from data.repository import DisasterRepository
    from search.hybrid_search import HybridSearchEngine

logger = logging.getLogger(__name__)


def search_disasters_semantic_logic(
    repo: "DisasterRepository",
    engine: Optional["HybridSearchEngine"],
    query: str,
) -> str:
    """Core logic for search_disasters_semantic — testable without FastMCP."""
    if not query or not query.strip():
        return "Please provide a non-empty search query."

    if engine is None:
        return "Semantic search is not available (indices not built)."

    results = engine.search(query.strip())

    if not results:
        return f"No matching disaster records found for query: '{query}'."

    df = repo.df
    lines = [
        f"Top {len(results)} semantically matched disasters for query: '{query}'\n"
    ]

    for i, result in enumerate(results, 1):
        record_id = result.get("record_id", "")
        score = result.get("score", 0.0)
        text = result.get("text", "")

        if not df.empty and "event_id" in df.columns:
            matching = df[df["event_id"] == record_id]
            if not matching.empty:
                row = matching.iloc[0]
                country = str(row.get("country") or "Unknown")
                dtype = str(row.get("disaster_type") or "Unknown").title()
                year = _get_year(row.get("start_date"))
                deaths = row.get("deaths")
                death_str = f", {_fmt_num(deaths)} deaths" if not _is_na(deaths) else ""
                lines.append(
                    f"  {i}. [score={score:.3f}] {dtype} in {country} ({year}){death_str}"
                )
                continue

        snippet = text[:120].rstrip() + ("..." if len(text) > 120 else "")
        lines.append(f"  {i}. [score={score:.3f}] {snippet}")

    return "\n".join(lines)


def register_rag_tool(
    mcp,
    repo: "DisasterRepository",
    engine: Optional["HybridSearchEngine"],
) -> None:
    """Register the search_disasters_semantic tool on the FastMCP instance."""

    @mcp.tool()
    def search_disasters_semantic(
        query: Annotated[
            str,
            "A natural language description of the disaster events you are looking for. "
            "E.g. 'devastating tsunami that hit coastal towns' or "
            "'prolonged drought causing famine in sub-Saharan Africa'.",
        ],
    ) -> str:
        """
        Semantic similarity search over embedded disaster records using hybrid retrieval
        (BM25 keyword + dense vector + Reciprocal Rank Fusion). Use this when the user's
        question is descriptive or narrative rather than structured (no specific country,
        date, or type). Returns the most relevant historical events with their details.
        """
        return search_disasters_semantic_logic(repo, engine, query)


def _get_year(val) -> str:
    if val is None:
        return "unknown"
    try:
        import pandas as pd

        if pd.isna(val):
            return "unknown"
        return str(pd.Timestamp(val).year)
    except Exception:
        return str(val)[:4]


def _is_na(val) -> bool:
    try:
        import pandas as pd

        return pd.isna(val)
    except (TypeError, ImportError):
        return val is None


def _fmt_num(val) -> str:
    try:
        n = float(val)
        if n >= 1_000_000:
            return f"{n / 1_000_000:.1f}M"
        if n >= 1_000:
            return f"{n / 1_000:.1f}K"
        return f"{int(n):,}"
    except (TypeError, ValueError):
        return str(val)
