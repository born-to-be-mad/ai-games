# TerraQuery Performance Baselines

Baselines measured under **5 concurrent users** using `TerraQueryBaselineSimulation`.
LLM provider: OpenAI `gpt-4o`. terra-mcp: local FastMCP on port 8200.

> Run simulations: `./gradlew :terra-infrastructure:gatlingRun -DTERRA_QUERY_BASE_URL=http://localhost:8080`

---

## End-to-End Chat Latency

| Query complexity | p50 target | p95 target | Notes |
|---|---|---|---|
| Simple (single-tool, 1 country) | ≤ 3 s | ≤ 12 s | e.g. "How many floods in Bangladesh in 2000?" |
| Complex (multi-tool, multi-country) | ≤ 8 s | ≤ 20 s | e.g. "Compare flood trends across 3 countries 1990–2020" |

Breakdown by phase:

| Phase | Expected share | Notes |
|---|---|---|
| DataRetrievalAgent (LLM + tools) | ~60% | 1–3 MCP tool calls typical |
| AnalysisSynthesisAgent (LLM + RAG) | ~35% | ≤ 1 RAG call for most queries |
| Overhead (serialization, DB, SSE) | ~5% | H2 in-memory, negligible |

---

## MCP Tool Call Latency (terra-mcp)

| Tool | p50 target | p95 target |
|---|---|---|
| `query_disasters` | ≤ 50 ms | ≤ 200 ms |
| `get_disaster_statistics` | ≤ 50 ms | ≤ 200 ms |
| `get_disaster_trends` | ≤ 80 ms | ≤ 200 ms |
| `compare_disasters_across_countries` | ≤ 100 ms | ≤ 200 ms |
| `get_deadliest_disasters` | ≤ 50 ms | ≤ 200 ms |
| `get_live_events` (EONET API) | ≤ 500 ms | ≤ 2 s | Network-bound; excludes from p95 SLA |
| `search_disasters_semantic` (FAISS+BM25) | ≤ 100 ms | ≤ 500 ms |

---

## Hybrid Search Latency (terra-mcp)

| Stage | p50 target | p95 target |
|---|---|---|
| BM25 retrieval | ≤ 10 ms | ≤ 30 ms |
| FAISS vector search | ≤ 30 ms | ≤ 80 ms |
| RRF fusion | ≤ 5 ms | ≤ 10 ms |
| **Total hybrid search** | **≤ 50 ms** | **≤ 150 ms** |

Index warm: FAISS index loaded into memory at startup (≤ 30 s cold start).
Index cache: LRU disk cache; cache hit skips index rebuild (~2 min saved).

---

## Agent Tool Call Budgets

| Agent | Max tool calls | Enforced by |
|---|---|---|
| DataRetrievalAgent | 8 per turn | System prompt instruction |
| AnalysisSynthesisAgent | 4 per turn | System prompt instruction |

Empirical averages (simple queries): DataRetrievalAgent uses ~2 tools; AnalysisSynthesisAgent uses ~1 RAG call.

---

## Context Window

| Strategy | When used | Overhead |
|---|---|---|
| `SLIDING_WINDOW` | ≤ windowSize messages | Zero |
| `SUMMARIZING` | > windowSize messages | +1 LLM call (~500 ms) |
| `HYBRID` (default) | > windowSize but ≤ 2× | Sliding only; summarize when > 2× |

Default `slidingWindowSize`: 10 messages.
HYBRID triggers summarization when conversation exceeds 20 messages (~10+ turns).

---

## How to Run

### Prerequisites
- terra-query running on port 8080: `./gradlew :terra-infrastructure:bootRun`
- terra-mcp running on port 8200: `cd terra-mcp && python server.py`
- Valid `OPENAI_API_KEY` (or configure alternative provider via `TERRA_QUERY_AI_PROVIDER`)

### Execute simulation
```bash
./gradlew :terra-infrastructure:gatlingRun \
  -DTERRA_QUERY_BASE_URL=http://localhost:8080

# Results: terra-infrastructure/build/reports/gatling/
```

### Record actual baselines
After first run, update the "Actual" column below:

| Metric | Target | Actual (date) |
|---|---|---|
| Simple p50 | ≤ 3 s | — |
| Simple p95 | ≤ 12 s | — |
| Complex p50 | ≤ 8 s | — |
| Complex p95 | ≤ 20 s | — |
| MCP tool call p95 | ≤ 200 ms | — |
| Hybrid search p95 | ≤ 500 ms | — |
