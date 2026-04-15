"""Integration tests: server tool registration and module import smoke tests."""

import sys
from pathlib import Path
from unittest.mock import MagicMock

sys.path.insert(0, str(Path(__file__).parent.parent.parent))


class TestAllToolsRegistered:
    """Verify all 7 tools register without errors."""

    def test_all_tools_registered(self):
        from mcp.server.fastmcp import FastMCP
        from tools.disaster_query import register_query_tool
        from tools.disaster_stats import register_stats_tools
        from tools.disaster_rag import register_rag_tool
        from tools.live_events import register_live_events_tool

        mcp = FastMCP("test-terra-mcp")
        mock_repo = MagicMock()
        mock_repo.df = MagicMock()

        # Should not raise
        register_query_tool(mcp, mock_repo)
        register_stats_tools(mcp, mock_repo)
        register_rag_tool(mcp, mock_repo, MagicMock())
        register_live_events_tool(mcp, MagicMock())

        tools = mcp._tool_manager.list_tools()
        tool_names = {t.name for t in tools}

        expected = {
            "query_disasters",
            "get_disaster_statistics",
            "get_deadliest_disasters",
            "get_disaster_trends",
            "compare_disasters_across_countries",
            "search_disasters_semantic",
            "get_live_events",
        }
        missing = expected - tool_names
        assert not missing, f"Missing tools: {missing}"

    def test_tool_count_is_7(self):
        from mcp.server.fastmcp import FastMCP
        from tools.disaster_query import register_query_tool
        from tools.disaster_stats import register_stats_tools
        from tools.disaster_rag import register_rag_tool
        from tools.live_events import register_live_events_tool

        mcp = FastMCP("test-terra-mcp-2")
        mock_repo = MagicMock()
        register_query_tool(mcp, mock_repo)
        register_stats_tools(mcp, mock_repo)
        register_rag_tool(mcp, mock_repo, MagicMock())
        register_live_events_tool(mcp, MagicMock())

        assert len(mcp._tool_manager.list_tools()) == 7


class TestLogicFunctionsImport:
    """Smoke test: all logic functions importable."""

    def test_query_logic_importable(self):
        from tools.disaster_query import query_disasters_logic

        assert callable(query_disasters_logic)

    def test_stats_logic_importable(self):
        from tools.disaster_stats import (
            get_disaster_statistics_logic,
            get_deadliest_disasters_logic,
            get_disaster_trends_logic,
            compare_disasters_across_countries_logic,
        )

        for fn in (
            get_disaster_statistics_logic,
            get_deadliest_disasters_logic,
            get_disaster_trends_logic,
            compare_disasters_across_countries_logic,
        ):
            assert callable(fn)

    def test_rag_logic_importable(self):
        from tools.disaster_rag import search_disasters_semantic_logic

        assert callable(search_disasters_semantic_logic)

    def test_live_events_logic_importable(self):
        from tools.live_events import get_live_events_logic

        assert callable(get_live_events_logic)
