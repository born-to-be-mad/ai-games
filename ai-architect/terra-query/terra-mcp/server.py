"""TerraQuery MCP server — FastMCP entry point."""

import logging
import os
import sys
from pathlib import Path
from typing import Literal, cast

# Ensure project root is on path for relative imports
sys.path.insert(0, str(Path(__file__).parent))

from mcp.server.fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from data.index.index_builder import build_indices
from data.index.index_cache import IndexCache
from data.loaders.eonet_client import EonetClient
from data.repository import DisasterRepository
from search.hierarchical_chunker import HierarchicalChunker
from search.hybrid_search import HybridSearchEngine
from search.rrf_config import RRFConfig
from tools.disaster_query import register_query_tool
from tools.disaster_rag import register_rag_tool
from tools.disaster_stats import register_stats_tools
from tools.live_events import register_live_events_tool

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

# -------------------------------------------------------------------------
# FastMCP instance
# -------------------------------------------------------------------------
mcp = FastMCP("terra-mcp", host="0.0.0.0", port=8080)


def startup() -> tuple[DisasterRepository, HybridSearchEngine | None, EonetClient]:
    """
    Startup sequence:
    1. Load CSVs from all loaders
    2. Compute data hash
    3. Get or build indices (FAISS + BM25) from cache
    4. Log quality report
    5. Return initialized components
    """
    logger.info("=== TerraQuery MCP Server starting ===")

    # 1. Load data
    repo = DisasterRepository()
    repo.load()

    # 2. Build / load search indices
    chunker = HierarchicalChunker()
    cache = IndexCache()
    data_files = repo.get_data_file_paths()

    engine: HybridSearchEngine | None = None
    if not repo.df.empty:
        logger.info(
            "Preparing search indices for %d records (first run may take several minutes)",
            len(repo.df),
        )
        data_hash = (
            IndexCache.compute_data_hash(*data_files) if data_files else "default"
        )
        logger.info("Using index cache key: %s", data_hash)
        indices = cache.get_or_build(
            data_hash,
            lambda: build_indices(repo.df, chunker),
        )
        repo.set_indices(indices)
        rrf_config = RRFConfig.from_env()
        engine = HybridSearchEngine(indices=indices, config=rrf_config)
        logger.info(
            "Search engine ready (BM25=%s, FAISS=%s)",
            indices.bm25 is not None,
            indices.faiss_index is not None,
        )
    else:
        logger.warning(
            "No disaster data loaded — search tools will return empty results"
        )

    eonet_client = EonetClient()
    logger.info("=== TerraQuery MCP Server ready ===")
    return repo, engine, eonet_client


# -------------------------------------------------------------------------
# Register tools (called at module import time so FastMCP discovers them)
# -------------------------------------------------------------------------
_repo, _engine, _eonet = startup()

register_query_tool(mcp, _repo)
register_stats_tools(mcp, _repo)
register_rag_tool(mcp, _repo, _engine)
register_live_events_tool(mcp, _eonet)


@mcp.custom_route("/health", methods=["GET"], include_in_schema=False)
async def health_check(_request: Request) -> Response:
    """Simple readiness endpoint for container health checks."""
    return JSONResponse(
        {
            "status": "ok",
            "service": "terra-mcp",
            "transport": os.getenv("MCP_TRANSPORT", "streamable-http"),
            "records": int(len(_repo.df)),
            "search_ready": _engine is not None,
        }
    )


if __name__ == "__main__":
    transport = cast(
        Literal["stdio", "sse", "streamable-http"],
        os.getenv("MCP_TRANSPORT", "streamable-http"),
    )
    logger.info("Starting server with transport=%s on port 8080", transport)
    mcp.run(transport=transport)
