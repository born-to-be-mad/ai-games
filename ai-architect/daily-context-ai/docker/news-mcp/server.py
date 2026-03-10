import os
import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("news-aggregator")

THENEWSAPI_KEY = os.environ.get("THENEWSAPI_KEY", "")
GNEWS_KEY = os.environ.get("GNEWS_KEY", "")
NEWSAPI_KEY = os.environ.get("NEWSAPI_KEY", "")


def _format_articles(articles: list[dict], source: str) -> str:
    if not articles:
        return f"No articles found from {source}."
    lines = [f"News from {source}:"]
    for i, a in enumerate(articles, 1):
        title = a.get("title", "No title")
        url = a.get("url", "")
        published = a.get("published_at") or a.get("publishedAt") or a.get("published_date", "")
        lines.append(f"  {i}. {title}")
        if published:
            lines.append(f"     Published: {published}")
        if url:
            lines.append(f"     URL: {url}")
    return "\n".join(lines)


@mcp.tool()
async def get_news_thenewsapi(query: str, count: int = 5) -> str:
    """Search for news articles using TheNewsAPI."""
    if not THENEWSAPI_KEY:
        return "TheNewsAPI key not configured. Set THENEWSAPI_KEY environment variable."

    count = max(1, min(count, 25))
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get("https://api.thenewsapi.com/v1/news/all", params={
            "api_token": THENEWSAPI_KEY,
            "search": query,
            "language": "en",
            "limit": count,
        })
        if resp.status_code == 401:
            return "Invalid TheNewsAPI key."
        resp.raise_for_status()
        data = resp.json()

    articles = data.get("data", [])
    return _format_articles(articles, "TheNewsAPI")


@mcp.tool()
async def get_news_gnews(query: str, count: int = 5) -> str:
    """Search for news articles using GNews."""
    if not GNEWS_KEY:
        return "GNews key not configured. Set GNEWS_KEY environment variable."

    count = max(1, min(count, 10))
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get("https://gnews.io/api/v4/search", params={
            "q": query,
            "token": GNEWS_KEY,
            "lang": "en",
            "max": count,
        })
        if resp.status_code == 403:
            return "Invalid GNews key."
        resp.raise_for_status()
        data = resp.json()

    articles = data.get("articles", [])
    # Normalize field names
    for a in articles:
        a["published_at"] = a.pop("publishedAt", "")
    return _format_articles(articles, "GNews")


@mcp.tool()
async def get_news_newsapi(query: str, count: int = 5) -> str:
    """Search for news articles using NewsAPI."""
    if not NEWSAPI_KEY:
        return "NewsAPI key not configured. Set NEWSAPI_KEY environment variable."

    count = max(1, min(count, 100))
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get("https://newsapi.org/v2/everything", params={
            "q": query,
            "apiKey": NEWSAPI_KEY,
            "language": "en",
            "pageSize": count,
        })
        if resp.status_code == 401:
            return "Invalid NewsAPI key."
        resp.raise_for_status()
        data = resp.json()

    if data.get("status") != "ok":
        return f"NewsAPI error: {data.get('message', 'Unknown error')}"

    articles = data.get("articles", [])
    return _format_articles(articles, "NewsAPI")


@mcp.tool()
async def get_all_news(query: str, count: int = 5, sources: str = "all") -> str:
    """
    Search for news from multiple sources.
    sources: comma-separated list of 'thenewsapi', 'gnews', 'newsapi', or 'all'
    """
    source_list = [s.strip().lower() for s in sources.split(",")]
    if "all" in source_list:
        source_list = ["thenewsapi", "gnews", "newsapi"]

    results = []
    for source in source_list:
        if source == "thenewsapi":
            result = await get_news_thenewsapi(query, count)
        elif source == "gnews":
            result = await get_news_gnews(query, count)
        elif source == "newsapi":
            result = await get_news_newsapi(query, count)
        else:
            result = f"Unknown source: {source}"
        results.append(result)

    return "\n\n".join(results)


mcp.run(transport="streamable-http", host="0.0.0.0", port=8080)
