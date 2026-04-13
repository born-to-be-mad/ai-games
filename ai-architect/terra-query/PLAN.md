# TerraQuery — Implementation Plan

## Vision

A production-ready agentic chatbot that answers natural disaster queries through **true multi-agent behavior**: specialized LLM agents own the reasoning loop, autonomously select tools, observe results, and iterate — rather than following hardcoded Java dispatch logic.

This project synthesizes:
- **RAG pipeline** from `rag-report-analyzer` (hexagonal architecture, vector store, chunked ingestion, evaluation framework)
- **MCP tool servers** from `daily-context-ai` (FastMCP in Python, Docker-hosted, Spring AI client)
- **Corrected agent pattern**: think → act → observe → loop → synthesize (LLM-driven, not Java-driven)
- **Multi-agent coordination**: specialized sub-agents (data retrieval + analysis/synthesis) with clear responsibilities

---

## Critical Design Correction (from daily-context review)

### ❌ What we did before (wrong)
```
Java OrchestratorService:
  if (intent.needsWeather) → call WeatherAgent
  if (intent.needsNews) → call NewsAgent
  → synthesize
```
The LLM classified intent but did not control flow. Agents were "Tool Callers" with no autonomy.

### ✅ What we build now (correct)
```
Supervisor agent receives: "Tell me about floods in Bangladesh and whether they're getting worse"
  ↓
  delegates to DataRetrievalAgent:
    thinks: "I need historical flood data for Bangladesh"
    acts:   calls query_disasters(type="flood", country="Bangladesh")
    observes: 142 records found
    thinks: "I need aggregated statistics too"
    acts:   calls get_disaster_statistics(type="flood", country="Bangladesh")
    observes: avg 2300 deaths/year, 18M affected
    thinks: "Trend data will answer the 'getting worse' question"
    acts:   calls get_disaster_trends(type="flood", country="Bangladesh")
    observes: yearly data 1970–2021
    thinks: "Check for any current events"
    acts:   calls get_live_events(type="flood")
    observes: 2 active events via NASA EONET
    returns: collected raw data + tool results
  ↓
  delegates to AnalysisAgent:
    thinks: "I have 4 data payloads to synthesize into a coherent answer"
    synthesizes: trend analysis showing increasing frequency, key statistics,
                 historical context, current situation
    returns: structured answer with citations and chart-ready data
  ↓
  supervisor merges: final response with sources, visualization hints
```

Spring AI's `ChatClient` with registered MCP tools handles the agent loops **automatically**. No Java if/else. Each agent decides what to call, when, and in what order, within its domain of responsibility.

---

## Multi-Agent Architecture

### Why Multi-Agent?

A single agent with 7+ tools and responsibilities for both data retrieval and analytical synthesis leads to:
- Long tool-call chains that consume context window
- Conflated responsibilities (fetching data vs. reasoning about it)
- Harder to tune: a system prompt that's good for data retrieval may be suboptimal for synthesis

### Agent Roles

```
┌─────────────────────────────────────────────────────────────┐
│                    SupervisorAgent                            │
│  Routes user queries, coordinates sub-agents, merges results │
│  Tools: delegate_to_retrieval, delegate_to_analysis          │
│  Budget: configurable max 3 delegation rounds                │
└──────────────┬──────────────────────┬───────────────────────┘
               │                      │
    ┌──────────▼──────────┐  ┌───────▼────────────────────┐
    │  DataRetrievalAgent  │  │  AnalysisSynthesisAgent     │
    │                      │  │                             │
    │  Responsibility:     │  │  Responsibility:            │
    │  - Fetch raw data    │  │  - Interpret retrieved data │
    │  - Query filtering   │  │  - Trend analysis           │
    │  - Source selection   │  │  - Comparative reasoning    │
    │                      │  │  - Citation generation       │
    │  Tools:              │  │  - Chart data structuring    │
    │  - query_disasters   │  │                             │
    │  - get_statistics    │  │  Tools:                     │
    │  - get_deadliest     │  │  - search_semantic (RAG)    │
    │  - get_trends        │  │  - (LLM reasoning only)     │
    │  - compare_countries │  │                             │
    │  - get_live_events   │  │  Input: raw data from       │
    │                      │  │  DataRetrievalAgent          │
    │  Budget: max 8 calls │  │  Budget: max 4 calls        │
    └──────────────────────┘  └─────────────────────────────┘
```

### Implementation

```java
// SupervisorAgentAdapter.java
@Component
public class SupervisorAgentAdapter implements AgentPort {

    private final ChatClient supervisorClient;
    private final DataRetrievalAgent dataAgent;
    private final AnalysisSynthesisAgent analysisAgent;
    private final AgentGuardrailsConfig guardrails;

    @Override
    public AgentResponse execute(String userQuery, List<ChatMessage> history) {
        var windowedHistory = guardrails.applyContextWindow(history);
        
        // Supervisor decides which agents to invoke and in what order
        // Spring AI handles the tool-call loop
        String answer = supervisorClient.prompt()
            .messages(windowedHistory)
            .user(userQuery)
            .call()
            .content();

        return AgentResponse.of(answer);
    }
}

// DataRetrievalAgent.java — has MCP tools, focused system prompt
@Component
public class DataRetrievalAgent {

    private final ChatClient retrievalClient;

    public DataRetrievalAgent(ChatModel chatModel, List<McpSyncClient> mcpClients,
                               AgentGuardrailsConfig guardrails) {
        var dataTools = mcpClients.stream()
            .flatMap(c -> new SyncMcpToolCallbackProvider(c).getToolCallbacks().stream())
            .filter(tool -> !tool.getName().equals("search_disasters_semantic"))
            .toArray(ToolCallback[]::new);

        this.retrievalClient = ChatClient.builder(chatModel)
            .defaultSystem(DATA_RETRIEVAL_SYSTEM_PROMPT)
            .defaultTools(dataTools)
            .defaultToolCallConfig(ToolCallingConfig.builder()
                .maxIterations(guardrails.getMaxRetrievalToolCalls())  // configurable, default 8
                .build())
            .build();
    }
}

// AnalysisSynthesisAgent.java — RAG tool + reasoning, different system prompt
@Component
public class AnalysisSynthesisAgent {

    private final ChatClient analysisClient;

    public AnalysisSynthesisAgent(ChatModel chatModel, List<McpSyncClient> mcpClients,
                                   AgentGuardrailsConfig guardrails) {
        var ragTools = mcpClients.stream()
            .flatMap(c -> new SyncMcpToolCallbackProvider(c).getToolCallbacks().stream())
            .filter(tool -> tool.getName().equals("search_disasters_semantic"))
            .toArray(ToolCallback[]::new);

        this.analysisClient = ChatClient.builder(chatModel)
            .defaultSystem(ANALYSIS_SYNTHESIS_SYSTEM_PROMPT)
            .defaultTools(ragTools)
            .defaultToolCallConfig(ToolCallingConfig.builder()
                .maxIterations(guardrails.getMaxAnalysisToolCalls())  // configurable, default 4
                .build())
            .build();
    }
}
```

---

## Agent Guardrails (configurable, not hardcoded)

All guardrail parameters are externalized to `application.yml` with sensible defaults:

```yaml
# application.yml
terra-query:
  agent:
    guardrails:
      # Tool call limits per agent per query (prevents runaway loops)
      max-supervisor-delegations: 3
      max-retrieval-tool-calls: 8
      max-analysis-tool-calls: 4
      # Token budgets
      max-output-tokens: 4096
      max-context-window-usage-percent: 80
      # Conversation window
      context-window-strategy: HYBRID  # SLIDING_WINDOW | SUMMARIZING | HYBRID
      sliding-window-size: 10          # last N messages kept verbatim
      # Cost & rate control
      max-queries-per-minute: 20
      daily-cost-cap-usd: 5.0
      # Timeout
      agent-timeout-seconds: 60
```

```java
// AgentGuardrailsConfig.java
@ConfigurationProperties(prefix = "terra-query.agent.guardrails")
public record AgentGuardrailsConfig(
    int maxSupervisorDelegations,
    int maxRetrievalToolCalls,
    int maxAnalysisToolCalls,
    int maxOutputTokens,
    int maxContextWindowUsagePercent,
    ContextWindowStrategy contextWindowStrategy,
    int slidingWindowSize,
    int maxQueriesPerMinute,
    BigDecimal dailyCostCapUsd,
    int agentTimeoutSeconds
) {
    public enum ContextWindowStrategy {
        SLIDING_WINDOW,   // keep last N messages
        SUMMARIZING,      // compress older messages via LLM
        HYBRID            // last N verbatim + summary of older (default)
    }
    
    public List<ChatMessage> applyContextWindow(List<ChatMessage> history) {
        return switch (contextWindowStrategy) {
            case SLIDING_WINDOW -> history.subList(
                Math.max(0, history.size() - slidingWindowSize), history.size());
            case SUMMARIZING -> summarizeAll(history);
            case HYBRID -> hybridWindow(history, slidingWindowSize);
        };
    }
}
```

---

## Tool Argument Validation

Validation follows a **defense-in-depth** approach — both sides validate, but the MCP server (tool owner) is the authority:

### MCP Server (Python) — authoritative validation

```python
# validation.py — Pydantic models for each tool
from pydantic import BaseModel, Field, field_validator
from typing import Optional

VALID_DISASTER_TYPES = {
    "flood", "earthquake", "storm", "drought", "wildfire",
    "volcano", "landslide", "epidemic", "extreme_temperature"
}

class DisasterQueryParams(BaseModel):
    disaster_type: Optional[str] = Field(None, max_length=50)
    country: Optional[str] = Field(None, max_length=100)
    year_from: Optional[int] = Field(None, ge=1900, le=2030)
    year_to: Optional[int] = Field(None, ge=1900, le=2030)
    limit: int = Field(20, ge=1, le=100)

    @field_validator("disaster_type")
    @classmethod
    def validate_disaster_type(cls, v):
        if v is not None and v.lower() not in VALID_DISASTER_TYPES:
            raise ValueError(f"Unknown disaster type: {v}. Valid: {VALID_DISASTER_TYPES}")
        return v.lower() if v else v

@mcp.tool()
def query_disasters(
    disaster_type: Annotated[str | None, "..."] = None,
    country: Annotated[str | None, "..."] = None,
    year_from: Annotated[int | None, "..."] = None,
    year_to: Annotated[int | None, "..."] = None,
    limit: Annotated[int, "..."] = 20
) -> str:
    params = DisasterQueryParams(
        disaster_type=disaster_type, country=country,
        year_from=year_from, year_to=year_to, limit=limit
    )
    # ... proceed with validated params
```

### Spring AI side — lightweight sanitization (from daily-context lesson)

```java
// ToolArgumentSanitizer.java — catches LLM hallucinated schema objects
// Reuses the SanitizingToolCallback pattern from daily-context-ai
// but only as a safety net, not as primary validation
@Component
public class ToolArgumentSanitizer implements ToolCallbackInterceptor {
    @Override
    public ToolCallback wrap(ToolCallback original) {
        return new SanitizingToolCallback(original);
    }
}
```

---

## Conversation Persistence (configurable scope)

```yaml
# application.yml
terra-query:
  conversation:
    persistence-scope: FULL  # MESSAGES_ONLY | FULL
    # MESSAGES_ONLY: only user messages + final synthesized assistant messages
    # FULL: includes tool call details (arguments, raw results, agent delegation chain)
```

```java
// ConversationPersistenceConfig.java
public enum PersistenceScope {
    MESSAGES_ONLY,  // Lightweight — only human-readable messages
    FULL            // Debug-friendly — includes tool calls, arguments, raw results
}

// JpaConversationAdapter stores based on scope:
@Override
public void save(Conversation conversation, PersistenceScope scope) {
    if (scope == PersistenceScope.FULL) {
        // Store: user messages, assistant messages, AND tool call records
        // Each tool call: toolName, arguments (JSON), result (JSON), latency_ms
        conversationEntity.setToolCalls(mapToolCalls(conversation.toolCalls()));
    }
    // Always store: user messages + final assistant messages
    conversationEntity.setMessages(mapMessages(conversation.messages()));
    repository.save(conversationEntity);
}
```

---

## Project Structure (renamed to terra-query)

```
terra-query/
├── terra-mcp/                         # Python FastMCP server (Docker)
│   ├── server.py                      # FastMCP entry point
│   ├── tools/
│   │   ├── disaster_query.py          # Structured CSV/DB query tools
│   │   ├── disaster_stats.py          # Aggregation & comparison tools
│   │   ├── disaster_rag.py            # Semantic search tool (hybrid)
│   │   └── live_events.py             # NASA EONET real-time tool
│   ├── data/
│   │   ├── loaders/
│   │   │   ├── base_loader.py         # Abstract loader interface (pluggable)
│   │   │   ├── eosdis_loader.py       # Kaggle EOSDIS CSV loader
│   │   │   ├── noaa_loader.py         # NOAA Storm Events loader
│   │   │   └── eonet_client.py        # NASA EONET HTTP client
│   │   ├── quality/
│   │   │   ├── deduplicator.py        # Fuzzy dedup across sources
│   │   │   ├── normalizer.py          # Country names → ISO 3166, null handling
│   │   │   └── quality_report.py      # Data quality metrics (logged at startup)
│   │   ├── index/
│   │   │   ├── index_builder.py       # Pre-compute & cache FAISS + BM25 indices
│   │   │   └── index_cache.py         # Persist indices to disk, load on restart
│   │   └── repository.py             # Unified in-memory data store (pandas)
│   ├── search/
│   │   ├── hybrid_search.py           # BM25 + FAISS + RRF engine
│   │   ├── hierarchical_chunker.py    # Child/parent chunk strategy
│   │   └── rrf_config.py             # A/B testable RRF parameters
│   ├── validation/
│   │   └── tool_params.py            # Pydantic validation for all tools
│   ├── tests/
│   │   ├── conftest.py                # Shared fixtures
│   │   ├── unit/
│   │   │   ├── test_disaster_query.py
│   │   │   ├── test_disaster_stats.py
│   │   │   ├── test_disaster_rag.py
│   │   │   ├── test_live_events.py
│   │   │   ├── test_hybrid_search.py
│   │   │   ├── test_hierarchical_chunker.py
│   │   │   ├── test_deduplicator.py
│   │   │   └── test_normalizer.py
│   │   └── integration/
│   │       ├── test_full_pipeline.py
│   │       └── test_server.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── terra-core/                        # Domain layer (zero Spring/framework deps)
│   └── src/main/java/com/aiarchitect/terraquery/
│       ├── model/
│       │   ├── ChatMessage.java
│       │   ├── Conversation.java
│       │   ├── AgentResponse.java
│       │   └── ToolCallRecord.java    # For FULL persistence scope
│       ├── port/
│       │   ├── in/
│       │   │   ├── ChatUseCase.java
│       │   │   ├── ConversationUseCase.java
│       │   │   └── EvaluationUseCase.java
│       │   └── out/
│       │       ├── AgentPort.java
│       │       ├── ConversationRepository.java
│       │       └── EvalJudgePort.java
│       └── service/
│           ├── ChatService.java
│           ├── ConversationService.java
│           └── EvaluationService.java
│
├── terra-infrastructure/              # Spring Boot + integrations
│   └── src/main/java/com/aiarchitect/terraquery/
│       ├── adapter/
│       │   ├── in/rest/
│       │   │   ├── ChatController.java
│       │   │   ├── ConversationController.java
│       │   │   └── EvaluationController.java
│       │   └── out/
│       │       ├── agent/
│       │       │   ├── SupervisorAgentAdapter.java    # Coordinates sub-agents
│       │       │   ├── DataRetrievalAgent.java        # MCP tools for data fetching
│       │       │   └── AnalysisSynthesisAgent.java    # RAG + reasoning
│       │       ├── persistence/
│       │       │   └── JpaConversationAdapter.java
│       │       ├── eval/
│       │       │   └── CrossProviderEvalAdapter.java
│       │       └── streaming/
│       │           └── SseStreamingAdapter.java       # SSE for real-time progress
│       ├── config/
│       │   ├── AgentConfig.java
│       │   ├── AgentGuardrailsConfig.java    # All limits externalized
│       │   ├── McpClientConfig.java
│       │   ├── EvalConfig.java               # Generator ≠ Evaluator providers
│       │   ├── ConversationPersistenceConfig.java
│       │   └── RateLimitConfig.java
│       └── TerraQueryApplication.java
│
├── terra-frontend/                    # React 19 chat UI
│   ├── src/
│   │   ├── components/
│   │   │   ├── ChatWindow.jsx
│   │   │   ├── MessageBubble.jsx
│   │   │   ├── SourceCitations.jsx
│   │   │   ├── ToolProgressIndicator.jsx  # Shows "Searching database..." etc.
│   │   │   └── DisasterStatsPanel.jsx
│   │   ├── charts/
│   │   │   ├── TrendChart.jsx             # Recharts AreaChart
│   │   │   ├── DisasterBarChart.jsx       # Recharts BarChart
│   │   │   ├── TypeBreakdown.jsx          # Recharts PieChart
│   │   │   └── ChoroplethMap.jsx          # react-simple-maps
│   │   ├── hooks/
│   │   │   ├── useConversation.js
│   │   │   ├── useDisasterViz.js
│   │   │   └── useSSE.js                  # EventSource hook for streaming
│   │   ├── api/
│   │   │   └── chatApi.js
│   │   └── App.jsx
│   └── package.json
│
├── api-spec/
│   └── terra-query-api.yaml           # OpenAPI 3.1 spec (contract-first)
│
├── eval/
│   ├── golden-dataset.json            # 30 Q&A pairs (LLM-generated, Wikipedia/web-verified)
│   └── eval-config.yml                # Nightly schedule + CI/CD trigger config
│
└── docker-compose.yml
```

---

## Dataset Strategy (Pluggable Sources)

### Current Sources

| Source | Format | Coverage | Access |
|--------|--------|----------|--------|
| **Kaggle EOSDIS** (primary) | CSV | Global, 1900–2021, ~22k events | Downloaded at startup |
| **NOAA Storm Events** (secondary) | CSV | USA, 1950–present, ~1M events | Downloaded at startup |
| **NASA EONET** (live) | REST API | Real-time, active events | HTTP via `eonet_client.py` |

### Pluggable Loader Architecture

New data sources can be added at any time by implementing the `BaseLoader` interface:

```python
# base_loader.py
from abc import ABC, abstractmethod
import pandas as pd

NORMALIZED_SCHEMA = [
    "event_id", "disaster_type", "subtype", "country", "country_iso3",
    "region", "start_date", "end_date", "deaths", "injured", "affected",
    "economic_damage_usd", "magnitude", "source", "source_event_id"
]

class BaseLoader(ABC):
    @abstractmethod
    def load(self) -> pd.DataFrame:
        """Load and normalize data to NORMALIZED_SCHEMA."""
        pass

    @abstractmethod
    def source_name(self) -> str:
        """Unique identifier for this data source (used in dedup)."""
        pass
```

Future candidate sources: EM-DAT (CRED/UCLouvain), GDACS, ReliefWeb API, FEMA Disaster Declarations.

### Deduplication Across Sources

```python
# deduplicator.py
from rapidfuzz import fuzz
import pandas as pd

class CrossSourceDeduplicator:
    """
    Identifies and merges duplicate events across data sources.
    Uses fuzzy matching on (date_range + location + disaster_type).
    """
    
    SIMILARITY_THRESHOLD = 85  # rapidfuzz score 0–100
    DATE_TOLERANCE_DAYS = 7    # events within 7 days = potential duplicate

    def deduplicate(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        For each pair of records from different sources:
        1. Check if disaster_type matches exactly
        2. Check if dates overlap within DATE_TOLERANCE_DAYS
        3. Check if location similarity > SIMILARITY_THRESHOLD
        4. If all three: merge into single record, preferring higher-detail source
        """
        # Group by disaster_type to reduce O(n²) comparisons
        groups = df.groupby("disaster_type")
        deduplicated = []
        for dtype, group in groups:
            deduplicated.append(self._deduplicate_group(group))
        
        result = pd.concat(deduplicated, ignore_index=True)
        logger.info(f"Deduplication: {len(df)} → {len(result)} records "
                     f"({len(df) - len(result)} duplicates merged)")
        return result

    def _merge_records(self, record_a, record_b) -> dict:
        """Merge two duplicate records, preferring non-null values and higher counts."""
        merged = {}
        for col in NORMALIZED_SCHEMA:
            val_a, val_b = record_a.get(col), record_b.get(col)
            if col in ("deaths", "injured", "affected", "economic_damage_usd"):
                merged[col] = max(val_a or 0, val_b or 0)  # prefer higher (more complete reporting)
            else:
                merged[col] = val_a if pd.notna(val_a) else val_b
        merged["source"] = f"{record_a['source']}+{record_b['source']}"
        return merged
```

### Null Handling Strategy

```python
# normalizer.py — distinguishes "zero" from "unknown"
class DataNormalizer:
    """
    Null handling rules:
    - deaths=0  means "zero confirmed deaths" (keep as 0)
    - deaths=NaN means "death toll unknown/not reported" (keep as NaN, don't convert to 0)
    - The distinction matters: "no deaths" ≠ "we don't know"
    
    Country normalization:
    - All country names → ISO 3166-1 alpha-3 via pycountry
    - Fuzzy match for non-standard names (e.g., "Burma" → "MMR")
    """
```

### Data Quality Report (logged at startup)

```
[INFO] === TerraQuery Data Quality Report ===
[INFO] EOSDIS:  22,431 records loaded, 847 with missing death toll (3.8%), 0 duplicate event_ids
[INFO] NOAA:    1,012,847 records loaded, 124,301 with missing death toll (12.3%)
[INFO] Merged:  1,035,278 total → 1,031,142 after dedup (4,136 cross-source duplicates)
[INFO] Coverage gaps: Somalia 1991–1999 (0 events, likely data gap not reality)
[INFO] =========================================
```

---

## MCP Server Tools (7 tools with rich descriptions)

The quality of tool descriptions is critical — the LLM uses them to decide what to call.

### 1. `query_disasters`
```python
@mcp.tool()
def query_disasters(
    disaster_type: Annotated[str | None, "Type of natural disaster to filter by. 
        Accepted values: 'flood', 'earthquake', 'storm', 'drought', 'wildfire', 
        'volcano', 'landslide', 'epidemic', 'extreme_temperature'. 
        Leave None to search all types."] = None,
    country: Annotated[str | None, "ISO 3166-1 alpha-3 country code (e.g. 'BGD' for 
        Bangladesh, 'USA', 'JPN') or full country name. Leave None for all countries."] = None,
    year_from: Annotated[int | None, "Start year (inclusive) for the search range. 
        Earliest available: 1900."] = None,
    year_to: Annotated[int | None, "End year (inclusive). Defaults to current year."] = None,
    limit: Annotated[int, "Maximum number of records to return. Default 20, max 100."] = 20
) -> str:
    """
    Search the natural disaster database for events matching the given criteria.
    Returns a structured list of disaster events including dates, location, death toll,
    number of people affected, and estimated economic damage. Use this tool when the 
    user asks about specific disaster events or wants to explore what disasters 
    occurred in a particular region or time period.
    """
```

### 2. `get_disaster_statistics`
```python
@mcp.tool()
def get_disaster_statistics(
    disaster_type: Annotated[str | None, "..."] = None,
    country: Annotated[str | None, "..."] = None,
    year_from: Annotated[int | None, "..."] = None,
    year_to: Annotated[int | None, "..."] = None
) -> str:
    """
    Compute aggregate statistics for natural disaster events matching the given filters.
    Returns: total event count, total deaths, total affected persons, total economic damage (USD),
    average deaths per event, worst single event, most frequent disaster type.
    Use this tool when the user asks about scale, totals, averages, or comparative impact.
    """
```

### 3. `get_deadliest_disasters`
```python
@mcp.tool()
def get_deadliest_disasters(
    n: Annotated[int, "Number of top deadliest events to return (1–50)."] = 10,
    disaster_type: Annotated[str | None, "..."] = None,
    year_from: Annotated[int | None, "..."] = None,
    year_to: Annotated[int | None, "..."] = None
) -> str:
    """
    Returns the N deadliest natural disaster events ordered by confirmed death toll (descending).
    Useful for answering 'what was the worst earthquake ever?' or 
    'top 5 deadliest floods in Asia' type questions.
    """
```

### 4. `get_disaster_trends`
```python
@mcp.tool()
def get_disaster_trends(
    disaster_type: Annotated[str, "Type of disaster to analyze trend for."],
    country: Annotated[str | None, "..."] = None,
    year_from: Annotated[int, "Start year for trend window."] = 1970,
    year_to: Annotated[int, "End year for trend window."] = 2021
) -> str:
    """
    Returns year-by-year event counts, death tolls, and affected population for a 
    given disaster type. Use this when the user asks 'are floods increasing?', 
    'how has the frequency of hurricanes changed?', or any question involving
    trends over time.
    """
```

### 5. `compare_disasters_across_countries`
```python
@mcp.tool()
def compare_disasters_across_countries(
    disaster_type: Annotated[str, "..."],
    countries: Annotated[list[str], "List of 2–5 country codes or names to compare."],
    year_from: Annotated[int | None, "..."] = None,
    year_to: Annotated[int | None, "..."] = None
) -> str:
    """
    Side-by-side comparison of disaster impact across multiple countries.
    Returns a table with per-country totals: events, deaths, affected, economic damage.
    Use when the user asks to compare how different countries are affected by
    a specific type of disaster.
    """
```

### 6. `search_disasters_semantic`
```python
@mcp.tool()
def search_disasters_semantic(
    query: Annotated[str, "A natural language description of the disaster events 
        you are looking for. E.g. 'devastating tsunami that hit coastal towns' or 
        'prolonged drought causing famine in sub-Saharan Africa'."]
) -> str:
    """
    Semantic similarity search over embedded disaster records using hybrid retrieval 
    (BM25 keyword + dense vector + Reciprocal Rank Fusion). Use this when the user's 
    question is descriptive or narrative rather than structured (no specific country, 
    date, or type). Returns the most relevant historical events with their details.
    """
```

### 7. `get_live_events`
```python
@mcp.tool()
def get_live_events(
    disaster_type: Annotated[str | None, "Filter live events by type. 
        NASA EONET categories: 'wildfires', 'severeStorms', 'volcanoes', 
        'earthquakes', 'floods', 'drought'. Leave None for all active events."] = None
) -> str:
    """
    Fetches currently active natural disaster events from NASA's Earth Observatory 
    Natural Event Tracker (EONET). Returns events happening right now or in the 
    last 30 days with their geographic coordinates and severity if available.
    Use when the user asks about 'current', 'ongoing', 'right now', or 'latest' events.
    Falls back gracefully if EONET API is unavailable.
    """
```

---

## Streaming & Real-Time Progress (SSE)

```java
// ChatController.java — SSE endpoint for streaming responses
@PostMapping(value = "/api/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChatEvent>> streamChat(@RequestBody ChatRequest request) {
    return chatUseCase.streamChat(request.conversationId(), request.message())
        .map(event -> ServerSentEvent.<ChatEvent>builder()
            .event(event.type().name())
            .data(event)
            .build());
}

// ChatEvent types:
// TOOL_CALL_START  → {"tool": "query_disasters", "agent": "DataRetrievalAgent"}
// TOOL_CALL_END    → {"tool": "query_disasters", "result_preview": "142 records found"}
// AGENT_THINKING   → {"agent": "AnalysisSynthesisAgent", "status": "synthesizing"}
// ANSWER_CHUNK     → {"text": "Between 1990 and 2010, Bangladesh experienced..."}
// ANSWER_COMPLETE  → {"sources": [...], "visualization": {...}}
```

```jsx
// useSSE.js — React hook for streaming
export function useSSE(url) {
  const [events, setEvents] = useState([]);
  const [answer, setAnswer] = useState("");
  
  const startStream = useCallback((message, conversationId) => {
    const eventSource = new EventSource(url);
    eventSource.addEventListener("TOOL_CALL_START", (e) => {
      setEvents(prev => [...prev, { type: "tool", ...JSON.parse(e.data) }]);
    });
    eventSource.addEventListener("ANSWER_CHUNK", (e) => {
      setAnswer(prev => prev + JSON.parse(e.data).text);
    });
    // ...
  }, [url]);
  
  return { events, answer, startStream };
}
```

---

## Embedding Model Choice

Based on best practices for structured disaster data (short-to-medium text, multilingual country names, technical terminology):

| Model | Dimensions | Speed | Quality | Use Case |
|-------|-----------|-------|---------|----------|
| **`bge-base-en-v1.5`** (BAAI) | 768 | Fast | High | **Default choice** — top MTEB scores at base size, good for English disaster records |
| `all-MiniLM-L6-v2` | 384 | Fastest | Good | Lightweight alternative, suitable for dev/testing |
| `nomic-embed-text` | 768 | Medium | High | **Ollama-compatible** — best choice for fully local deployment |

**Decision:** Use `bge-base-en-v1.5` via `sentence-transformers` as default. Configurable to `nomic-embed-text` for Ollama-only setups. Embeddings are computed **once at startup** and cached to disk (see Index Caching below).

```yaml
# application.yml
terra-query:
  mcp:
    embedding-model: bge-base-en-v1.5   # or nomic-embed-text for Ollama
    embedding-cache-dir: /data/indices/  # persist to disk
```

---

## Hybrid Search with A/B Testable RRF

```python
# rrf_config.py — externalized, A/B testable
from dataclasses import dataclass

@dataclass
class RRFConfig:
    """
    Reciprocal Rank Fusion parameters.
    k: smoothing constant (higher = more weight to lower-ranked docs).
    Default k=60 is from the original Cormack et al. paper.
    bm25_weight / vector_weight: relative contribution of each retriever.
    """
    k: int = 60
    bm25_weight: float = 1.0
    vector_weight: float = 1.0
    top_k_per_retriever: int = 20  # candidates from each before fusion
    final_top_k: int = 10          # results after fusion

    @classmethod
    def from_env(cls):
        """Load from environment for A/B testing without code changes."""
        return cls(
            k=int(os.getenv("RRF_K", "60")),
            bm25_weight=float(os.getenv("RRF_BM25_WEIGHT", "1.0")),
            vector_weight=float(os.getenv("RRF_VECTOR_WEIGHT", "1.0")),
        )

# Usage in HybridSearchEngine:
class HybridSearchEngine:
    def __init__(self, records, embedding_model, config: RRFConfig = None):
        self.config = config or RRFConfig.from_env()
        # ... build BM25 + FAISS indices

    def search(self, query: str) -> list[dict]:
        # ... BM25 + FAISS retrieval ...
        
        # Weighted RRF fusion
        for rank, idx in enumerate(bm25_top):
            rrf_scores[idx] += self.config.bm25_weight / (self.config.k + rank + 1)
        for rank, idx in enumerate(dense_top):
            rrf_scores[idx] += self.config.vector_weight / (self.config.k + rank + 1)
```

---

## Hierarchical Chunking (handling short records)

**Problem:** Many EOSDIS records are 1–3 sentences (~30–80 tokens), far shorter than the 128-token child chunk size. Naive splitting would create empty or trivially small chunks.

**Solution:** Adaptive chunking with a minimum threshold:

```python
# hierarchical_chunker.py
@dataclass
class ChunkPair:
    child_id: str
    child_text: str        # ≤128 tokens — goes into vector index
    parent_id: str
    parent_text: str       # ≤512 tokens — sent to LLM on retrieval

class HierarchicalChunker:
    PARENT_SIZE = 512
    CHILD_SIZE = 128
    MIN_CHILD_SIZE = 32     # below this, record is its own parent AND child
    PARENT_OVERLAP = 64
    CHILD_OVERLAP = 16

    def chunk(self, text: str, metadata: dict) -> list[ChunkPair]:
        token_count = len(self._tokenize(text))

        # Short record strategy: if text < MIN_CHILD_SIZE tokens,
        # the record is both parent and child (no splitting)
        if token_count < self.MIN_CHILD_SIZE:
            return [ChunkPair(
                child_id=f"c_0_0",
                child_text=text,
                parent_id=f"p_0",
                parent_text=text  # same text — no loss
            )]
        
        # Medium record strategy: if text < PARENT_SIZE tokens,
        # single parent, split into children normally
        if token_count < self.PARENT_SIZE:
            children = self._split(text, size=self.CHILD_SIZE, overlap=self.CHILD_OVERLAP)
            return [ChunkPair(
                child_id=f"c_0_{i}",
                child_text=child,
                parent_id=f"p_0",
                parent_text=text  # whole record is the parent
            ) for i, child in enumerate(children)]

        # Long record strategy: split into parents, then children
        parents = self._split(text, size=self.PARENT_SIZE, overlap=self.PARENT_OVERLAP)
        pairs = []
        for p_idx, parent in enumerate(parents):
            children = self._split(parent, size=self.CHILD_SIZE, overlap=self.CHILD_OVERLAP)
            for c_idx, child in enumerate(children):
                pairs.append(ChunkPair(
                    child_id=f"c_{p_idx}_{c_idx}",
                    child_text=child,
                    parent_id=f"p_{p_idx}",
                    parent_text=parent
                ))
        return pairs

    def _enrich_text(self, record: dict) -> str:
        """
        Convert structured record to natural language for embedding.
        A bare CSV row like 'Flood,BGD,1998,1050,30000000' embeds poorly.
        Enriched: 'A flood in Bangladesh in 1998 killed 1,050 people 
        and affected 30,000,000. Economic damage: $5.9B USD.'
        """
        return (
            f"A {record['disaster_type']} in {record['country']} "
            f"{'from ' + record['start_date'] + ' to ' + record['end_date'] if record.get('end_date') else 'on ' + record['start_date']}. "
            f"Deaths: {record.get('deaths', 'unknown')}. "
            f"Affected: {record.get('affected', 'unknown')}. "
            f"Economic damage: ${record.get('economic_damage_usd', 'unknown')} USD."
        )
```

---

## Index Caching (fast startup)

**Problem:** Embedding 1M+ records at startup takes minutes. Rebuilding BM25 index on every restart is wasteful.

**Solution:** Pre-compute indices and persist to disk. Only rebuild when data changes.

```python
# index_cache.py
import hashlib, pickle
from pathlib import Path

class IndexCache:
    def __init__(self, cache_dir: Path = Path("/data/indices")):
        self.cache_dir = cache_dir
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def get_or_build(self, data_hash: str, builder_fn):
        """
        Load cached index if data hasn't changed; otherwise rebuild and cache.
        data_hash = MD5 of concatenated CSV files.
        """
        cache_path = self.cache_dir / f"index_{data_hash}.pkl"
        if cache_path.exists():
            logger.info(f"Loading cached indices from {cache_path}")
            return pickle.loads(cache_path.read_bytes())
        
        logger.info("Building indices from scratch (first run or data changed)...")
        indices = builder_fn()
        cache_path.write_bytes(pickle.dumps(indices))
        logger.info(f"Indices cached to {cache_path}")
        return indices

    @staticmethod
    def compute_data_hash(*file_paths: Path) -> str:
        h = hashlib.md5()
        for p in sorted(file_paths):
            h.update(p.read_bytes())
        return h.hexdigest()
```

**Startup sequence:**
1. Load CSVs → compute data hash
2. Check index cache → if hit, load pre-built FAISS + BM25 in ~2 seconds
3. If miss (first run or new data), build indices (~3–5 min for 1M records), cache for next time
4. NASA EONET → fetched live per request (no startup caching)

---

## Evaluation Framework

### Golden Dataset (LLM-generated, human-verified)

```json
// eval/golden-dataset.json — 30 Q&A pairs
{
  "version": "1.0",
  "generation_method": "GPT-4o generated, verified against Wikipedia and EM-DAT",
  "entries": [
    {
      "id": "GD-001",
      "question": "What was the deadliest earthquake in the 21st century?",
      "expected_answer": "The 2010 Haiti earthquake on January 12, 2010, with an estimated death toll of 220,000–316,000 people.",
      "verification_sources": ["Wikipedia: 2010 Haiti earthquake", "EM-DAT database"],
      "required_tools": ["query_disasters", "get_deadliest_disasters"],
      "difficulty": "easy"
    },
    {
      "id": "GD-015",
      "question": "Are floods becoming more frequent in South Asia compared to 1970?",
      "expected_answer": "Yes, flood frequency in South Asia has increased significantly. Between 1970–1990, the region averaged ~12 major flood events per year; between 2000–2021, this rose to ~28 events per year.",
      "verification_sources": ["EOSDIS trend data", "IPCC AR6 WG2 Chapter 10"],
      "required_tools": ["get_disaster_trends", "get_disaster_statistics"],
      "difficulty": "medium"
    }
    // ... 28 more entries
  ]
}
```

### Target Evaluation Thresholds (baselines)

| Metric | Target | Minimum Acceptable | Description |
|--------|--------|-------------------|-------------|
| **Context Precision** | ≥ 0.80 | ≥ 0.70 | % of retrieved chunks actually relevant to the question |
| **Context Recall** | ≥ 0.75 | ≥ 0.65 | % of expected-answer facts present in retrieved context |
| **Faithfulness** | ≥ 0.85 | ≥ 0.75 | Degree answer stays grounded in context (no hallucination) |
| **Answer Relevance** | ≥ 0.80 | ≥ 0.70 | Degree answer addresses the actual question asked |

These thresholds are based on published RAGAS benchmarks for domain-specific QA systems. Scores below "minimum acceptable" trigger CI/CD warnings. Scores below target trigger investigation but don't block.

### Cross-Provider Evaluation

```java
// EvalConfig.java — generator ≠ evaluator, always
@Configuration
public class EvalConfig {

    @Bean("generatorClient")
    public ChatClient generatorClient(@Qualifier("openAiChatModel") ChatModel openAi) {
        return ChatClient.builder(openAi).build();
    }

    @Bean("evaluatorClient")
    public ChatClient evaluatorClient(@Qualifier("anthropicChatModel") ChatModel anthropic) {
        return ChatClient.builder(anthropic).build();
    }
}
```

### Evaluation Schedule

- **Nightly:** Full 30-question golden dataset evaluation, results stored in DB and logged
- **CI/CD (manual trigger):** Subset of 10 "canary" questions for fast feedback on PRs
- **On data change:** When new CSV sources are added, trigger full evaluation to detect regressions

```yaml
# eval/eval-config.yml
schedules:
  nightly:
    cron: "0 2 * * *"
    dataset: golden-dataset.json
    questions: all
  ci-canary:
    trigger: manual
    dataset: golden-dataset.json
    questions: [GD-001, GD-005, GD-010, GD-015, GD-020, GD-025, GD-028, GD-029, GD-030, GD-002]
  data-change:
    trigger: on-data-hash-change
    dataset: golden-dataset.json
    questions: all
```

---

## API Contract (OpenAPI spec first)

```yaml
# api-spec/terra-query-api.yaml (OpenAPI 3.1)
openapi: "3.1.0"
info:
  title: TerraQuery API
  version: "1.0.0"
  description: Natural disaster research assistant with agentic multi-tool reasoning

paths:
  /api/v1/chat:
    post:
      summary: Send a message and get an agent response
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/ChatRequest"
      responses:
        "200":
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ChatResponse"

  /api/v1/chat/stream:
    post:
      summary: Stream agent response via SSE
      responses:
        "200":
          content:
            text/event-stream:
              schema:
                $ref: "#/components/schemas/ChatEvent"

  /api/v1/conversations:
    get:
      summary: List all conversations
  
  /api/v1/conversations/{id}:
    get:
      summary: Get full conversation with history
    delete:
      summary: Delete a conversation

  /api/v1/evaluation/run:
    post:
      summary: Trigger evaluation run (CI/CD or manual)
  
  /api/v1/evaluation/results:
    get:
      summary: Get latest evaluation results

  /api/v1/health:
    get:
      summary: Health check (includes MCP server and data source status)

components:
  schemas:
    ChatRequest:
      type: object
      properties:
        conversationId:
          type: string
          format: uuid
          nullable: true
        message:
          type: string
      required: [message]

    ChatResponse:
      type: object
      properties:
        conversationId:
          type: string
          format: uuid
        answer:
          type: string
        toolsUsed:
          type: array
          items:
            type: string
        sources:
          type: array
          items:
            type: string
        agentChain:
          type: array
          items:
            type: string
          description: "Agents involved: e.g. ['SupervisorAgent', 'DataRetrievalAgent', 'AnalysisSynthesisAgent']"
        visualization:
          $ref: "#/components/schemas/VisualizationHint"
          nullable: true

    ChatEvent:
      type: object
      properties:
        type:
          type: string
          enum: [TOOL_CALL_START, TOOL_CALL_END, AGENT_THINKING, ANSWER_CHUNK, ANSWER_COMPLETE]
        data:
          type: object

    VisualizationHint:
      type: object
      description: "Structured data the frontend can use to render charts"
      properties:
        chartType:
          type: string
          enum: [bar, line, pie, choropleth, none]
        data:
          type: array
          items:
            type: object
```

---

## Backend Architecture (Hexagonal + Multi-Agent)

```
┌──────────────────────────────────────────────────────────────────┐
│                       Domain Layer (terra-core)                    │
│  ChatService ──uses──► AgentPort (interface)                      │
│  ConversationService ──uses──► ConversationRepository (iface)     │
│  EvaluationService ──uses──► EvalJudgePort (iface)                │
│  ChatMessage, Conversation, AgentResponse, ToolCallRecord (VOs)   │
└──────────────────────────┬───────────────────────────────────────┘
                           │ implements
┌──────────────────────────▼───────────────────────────────────────┐
│                Infrastructure Layer (terra-infrastructure)         │
│                                                                    │
│  SupervisorAgentAdapter (implements AgentPort)                     │
│    ├── DataRetrievalAgent ──► MCP tools (query, stats, trends)    │
│    └── AnalysisSynthesisAgent ──► RAG tool + LLM reasoning        │
│                                                                    │
│  AgentGuardrailsConfig ──► loop limits, token budgets, timeouts   │
│  SseStreamingAdapter ──► SSE to frontend                          │
│  CrossProviderEvalAdapter ──► OpenAI generates, Anthropic judges  │
│  JpaConversationAdapter ──► H2 (dev) / PostgreSQL (prod)          │
│  ChatController, ConversationController ──► REST API              │
└──────────────────────────────────────────────────────────────────┘
```

**Domain enforced by ArchUnit** (same 5-rule pattern from rag-report-analyzer):
- Domain has zero Spring imports
- Services depend only on port interfaces
- Adapters only in infrastructure package
- No circular dependencies
- Port interfaces must be interfaces (not abstract classes)

---

## Test Strategy

### Test Pyramid

```
                    ┌─────────────────────┐
                    │   Live LLM Suite    │  Weekly (GitHub Action, manual trigger)
                    │   Real API calls    │  Validates answer quality, not just pipeline
                    └──────────┬──────────┘
                 ┌─────────────┴─────────────┐
                 │   Integration Tests        │  Per PR + nightly
                 │   @SpringBootTest          │  WireMock LLM stubs + real vector store
                 │   pytest integration/      │  Real CSV + real embeddings
                 └─────────────┬──────────────┘
           ┌───────────────────┴───────────────────┐
           │          Unit Tests                     │  Per commit
           │   Domain services (zero Spring)         │
           │   Tool logic, chunker, dedup, search    │
           │   ArchUnit boundary enforcement         │
           └───────────────────┬────────────────────┘
      ┌────────────────────────┴────────────────────────┐
      │            Mutation Tests (PIT + mutmut)          │  Nightly alongside eval
      │   Validates test quality, not just coverage       │
      │   Runs from Phase 1 onward (no waiting)           │
      └───────────────────────────────────────────────────┘
```

### MCP Server (Python / pytest)

```
terra-mcp/tests/
├── conftest.py                       # Sample CSV fixtures, mocked EONET, stub embeddings
├── unit/
│   ├── test_disaster_query.py        # query_disasters tool — filter logic, edge cases
│   ├── test_disaster_stats.py        # aggregation correctness, empty results
│   ├── test_disaster_rag.py          # hybrid search: BM25 vs vector vs RRF fusion
│   ├── test_live_events.py           # EONET client with httpx mocking + fallback behavior
│   ├── test_hybrid_search.py         # RRF fusion correctness, different k values
│   ├── test_hierarchical_chunker.py  # short/medium/long records, boundary cases
│   ├── test_deduplicator.py          # cross-source dedup, merge strategy
│   └── test_normalizer.py           # country names, null handling rules
└── integration/
    ├── test_full_pipeline.py         # Real CSV → chunk → embed → hybrid search → result
    └── test_server.py                # FastMCP tool calls end-to-end against loaded data
```

### Backend (Java / JUnit 5)

```
terra-core/tests:
├── ChatServiceTest                   # Domain unit tests (zero Spring)
├── ConversationServiceTest           # Pure domain logic
└── EvaluationServiceTest             # Eval orchestration logic

terra-infrastructure/tests:
├── unit/
│   ├── SupervisorAgentAdapterTest    # Mock sub-agents; verify delegation logic
│   ├── DataRetrievalAgentTest        # Mock ChatModel; assert MCP tools registered
│   ├── AnalysisSynthesisAgentTest    # Mock ChatModel; assert RAG tool registered
│   ├── ChatControllerTest            # MockMvc REST layer
│   └── JpaConversationAdapterTest    # @DataJpaTest with H2 (both persistence scopes)
├── integration/
│   ├── MultiAgentIntegrationTest     # Full supervisor → retrieval → analysis flow
│   │                                 # (SimpleVectorStore + WireMock LLM, no real API)
│   ├── HybridSearchIntegrationTest   # RRF outperforms vector-only on keyword-heavy queries
│   └── StreamingIntegrationTest      # SSE event sequence verification
└── arch/
    └── HexagonalArchitectureTest     # ArchUnit — 5 hexagonal boundary rules
```

### Live LLM Test Suite (GitHub Action, manual + weekly)

Tests that call real LLM APIs to validate **answer quality**, not just pipeline correctness. WireMock stubs prove the plumbing works; live tests prove the LLM actually produces useful answers.

```yaml
# .github/workflows/live-llm-tests.yml
name: Live LLM Test Suite
on:
  schedule:
    - cron: "0 4 * * 0"    # Every Sunday at 04:00 UTC
  workflow_dispatch:         # Manual trigger from GitHub Actions UI
    inputs:
      question_subset:
        description: "Run full or canary subset"
        required: false
        default: "canary"
        type: choice
        options:
          - full
          - canary

env:
  OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}

jobs:
  live-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Start MCP server with real data
        run: docker compose up -d terra-mcp
        working-directory: terra-query

      - name: Wait for MCP server healthy
        run: |
          for i in $(seq 1 30); do
            curl -sf http://localhost:8100/health && break || sleep 5
          done

      - name: Run live LLM answer quality tests
        run: ./gradlew :terra-infrastructure:liveTest \
          -Psubset=${{ github.event.inputs.question_subset || 'canary' }}
        working-directory: terra-query

      - name: Upload evaluation report
        uses: actions/upload-artifact@v4
        with:
          name: live-llm-report-${{ github.run_number }}
          path: terra-query/terra-infrastructure/build/reports/live-llm/
```

```java
// LiveLlmAnswerQualityTest.java — NOT run in normal CI, only via GitHub Action
@Tag("live-llm")                    // excluded from default test task
@SpringBootTest
@ActiveProfiles("live-test")        // uses real LLM API keys, real MCP server
class LiveLlmAnswerQualityTest {

    @Autowired ChatUseCase chatUseCase;
    @Autowired CrossProviderEvalAdapter evalAdapter;

    @ParameterizedTest
    @MethodSource("goldenDatasetProvider")
    void answerQualityMeetsThresholds(GoldenEntry entry) {
        AgentResponse response = chatUseCase.chat(entry.question(), null);

        EvalResult eval = evalAdapter.evaluate(
            entry.question(), response.answer(),
            entry.expectedAnswer(), response.retrievedContexts()
        );

        assertThat(eval.contextPrecision())
            .as("Context precision for: " + entry.id())
            .isGreaterThanOrEqualTo(0.70);    // minimum acceptable
        assertThat(eval.faithfulness())
            .as("Faithfulness for: " + entry.id())
            .isGreaterThanOrEqualTo(0.75);
        assertThat(eval.answerRelevance())
            .as("Answer relevance for: " + entry.id())
            .isGreaterThanOrEqualTo(0.70);
    }
}
```

### Mutation Testing (from Phase 1 onward)

Mutation testing validates that tests actually catch bugs, not just cover lines. Run alongside nightly evaluation — no waiting for coverage targets.

```xml
<!-- build.gradle.kts (backend) — PIT mutation testing -->
plugins {
    id("info.solidsoft.pitest") version "1.15.0"
}

pitest {
    targetClasses.set(listOf("com.aiarchitect.terraquery.domain.*"))
    targetTests.set(listOf("com.aiarchitect.terraquery.*Test"))
    mutators.set(listOf("DEFAULTS"))         // standard mutators
    timestampedReports.set(false)
    outputFormats.set(listOf("HTML", "XML"))
    threads.set(4)
    // Target: ≥70% mutation kill rate on domain layer
    mutationThreshold.set(70)
}
```

```toml
# terra-mcp/pyproject.toml — mutmut configuration
[tool.mutmut]
paths_to_mutate = "tools/,data/,search/"
tests_dir = "tests/"
runner = "python -m pytest tests/ -x --timeout=30"
# Target: ≥65% mutation kill rate on tool + search logic
```

```yaml
# .github/workflows/nightly.yml (added to existing nightly job)
  mutation-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: PIT mutation tests (Java domain)
        run: ./gradlew pitest
        working-directory: terra-query

      - name: mutmut (Python MCP server)
        run: |
          pip install mutmut
          mutmut run --no-progress
          mutmut results
        working-directory: terra-query/terra-mcp

      - name: Upload mutation reports
        uses: actions/upload-artifact@v4
        with:
          name: mutation-report-${{ github.run_number }}
          path: |
            terra-query/terra-infrastructure/build/reports/pitest/
            terra-query/terra-mcp/.mutmut-cache/
```

### Performance & Load Testing

**When:** Performance baselines after Phase 3 (multi-agent integration working). Load tests after Phase 6 (Docker Compose stack).

**Tools:** Gatling (JVM-native, scripted scenarios) for HTTP load testing. `pytest-benchmark` for MCP server micro-benchmarks.

#### Performance Targets

| Metric | Target | Maximum Acceptable | Conditions |
|--------|--------|--------------------|------------|
| **p50 latency (simple query)** | ≤ 3s | ≤ 5s | Single tool call (e.g., "deadliest earthquake ever") |
| **p95 latency (complex query)** | ≤ 12s | ≤ 20s | Multi-tool chain (e.g., trend + comparison + live events) |
| **p99 latency** | ≤ 25s | ≤ 45s | Worst-case multi-agent flow with RAG |
| **MCP tool response (no LLM)** | ≤ 200ms | ≤ 500ms | `query_disasters` with filters on in-memory data |
| **Hybrid search latency** | ≤ 500ms | ≤ 1s | BM25 + FAISS + RRF fusion, 1M records |
| **Throughput** | ≥ 10 req/s | ≥ 5 req/s | Concurrent users, sustained 5 minutes |
| **Startup (cached indices)** | ≤ 5s | ≤ 10s | MCP server with pre-built index cache |
| **Startup (cold, 1M records)** | ≤ 5min | ≤ 10min | First run, no cached indices |

#### Gatling Load Test

```scala
// TerraQuerySimulation.scala
class TerraQuerySimulation extends Simulation {

  val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")

  val simpleQueries = csv("test-data/simple-queries.csv").random
  val complexQueries = csv("test-data/complex-queries.csv").random

  val chatProtocol = http.baseUrl(baseUrl)
    .header("Content-Type", "application/json")

  val simpleQueryScenario = scenario("Simple disaster queries")
    .feed(simpleQueries)
    .exec(
      http("chat-simple")
        .post("/api/v1/chat")
        .body(StringBody("""{"message": "${query}"}"""))
        .check(status.is(200))
        .check(jsonPath("$.answer").exists)
        .check(responseTimeInMillis.lte(5000))
    )

  val complexQueryScenario = scenario("Complex multi-tool queries")
    .feed(complexQueries)
    .exec(
      http("chat-complex")
        .post("/api/v1/chat")
        .body(StringBody("""{"message": "${query}"}"""))
        .check(status.is(200))
        .check(jsonPath("$.toolsUsed").exists)
        .check(responseTimeInMillis.lte(20000))
    )

  setUp(
    simpleQueryScenario.inject(
      rampUsers(20).during(60),          // ramp to 20 users over 1 min
      constantUsersPerSec(5).during(300) // sustain 5 req/s for 5 min
    ),
    complexQueryScenario.inject(
      rampUsers(10).during(60),
      constantUsersPerSec(2).during(300)
    )
  ).protocols(chatProtocol)
   .assertions(
     global.responseTime.percentile(50).lt(3000),
     global.responseTime.percentile(95).lt(12000),
     global.successfulRequests.percent.gt(95)
   )
}
```

#### MCP Server Micro-Benchmarks

```python
# tests/benchmark/test_performance.py
import pytest

@pytest.fixture(scope="module")
def loaded_repo(sample_csv_1m):
    """Load full 1M-record dataset for realistic benchmarks."""
    return DisasterRepository.from_csv(sample_csv_1m)

class TestMcpPerformance:
    def test_query_disasters_latency(self, benchmark, loaded_repo):
        """query_disasters with filters should complete in < 200ms."""
        result = benchmark(
            loaded_repo.query,
            disaster_type="earthquake", country="JPN",
            year_from=1990, year_to=2020, limit=20
        )
        assert benchmark.stats["median"] < 0.2  # 200ms

    def test_hybrid_search_latency(self, benchmark, hybrid_engine):
        """Hybrid search (BM25 + FAISS + RRF) should complete in < 500ms."""
        result = benchmark(
            hybrid_engine.search,
            query="devastating tsunami coastal Japan", top_k=10
        )
        assert benchmark.stats["median"] < 0.5  # 500ms

    def test_startup_with_cached_index(self, benchmark, cached_index_dir):
        """Loading pre-built indices from disk should take < 5s."""
        result = benchmark(IndexCache(cached_index_dir).load)
        assert benchmark.stats["median"] < 5.0
```

#### GitHub Action for Performance Tests

```yaml
# .github/workflows/performance.yml
name: Performance Tests
on:
  workflow_dispatch:
    inputs:
      duration_seconds:
        description: "Load test duration (seconds)"
        default: "300"
  schedule:
    - cron: "0 3 * * 1"    # Every Monday at 03:00 UTC

jobs:
  performance:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4

      - name: Start full stack
        run: docker compose up -d
        working-directory: terra-query

      - name: Wait for healthy
        run: |
          for i in $(seq 1 60); do
            curl -sf http://localhost:8080/api/v1/health && break || sleep 5
          done

      - name: MCP micro-benchmarks
        run: |
          docker compose exec terra-mcp \
            pytest tests/benchmark/ --benchmark-json=benchmark.json
        working-directory: terra-query

      - name: Gatling load test
        run: ./gradlew gatlingRun \
          -DbaseUrl=http://localhost:8080 \
          -Dduration=${{ github.event.inputs.duration_seconds || '300' }}
        working-directory: terra-query

      - name: Upload reports
        uses: actions/upload-artifact@v4
        with:
          name: perf-report-${{ github.run_number }}
          path: |
            terra-query/build/reports/gatling/
            terra-query/terra-mcp/benchmark.json
```

### Memory Profiling (mandatory, Phase 1 deliverable)

Memory profiling of the MCP server is a **required action item** in Phase 1 — not optional, not deferred.

```python
# terra-mcp/profiling/memory_profile.py
"""
Mandatory profiling script. Run after data loading to establish
memory baseline and set Docker resource limits.

Usage: python -m profiling.memory_profile --csv-dir data/
Output: profiling_report.json with per-component memory breakdown
"""
import tracemalloc
import psutil
import json
from pathlib import Path

def profile_memory(csv_dir: str):
    process = psutil.Process()
    report = {}

    # Baseline
    report["baseline_mb"] = process.memory_info().rss / 1024 / 1024

    # 1. Load CSVs into pandas
    tracemalloc.start()
    repo = DisasterRepository.from_directory(csv_dir)
    current, peak = tracemalloc.get_traced_memory()
    report["pandas_dataframes"] = {
        "current_mb": current / 1024 / 1024,
        "peak_mb": peak / 1024 / 1024,
        "record_count": len(repo),
    }
    tracemalloc.stop()

    # 2. Build BM25 index
    tracemalloc.start()
    bm25_index = build_bm25(repo)
    current, peak = tracemalloc.get_traced_memory()
    report["bm25_index"] = {
        "current_mb": current / 1024 / 1024,
        "peak_mb": peak / 1024 / 1024,
    }
    tracemalloc.stop()

    # 3. Build FAISS index
    tracemalloc.start()
    faiss_index = build_faiss(repo)
    current, peak = tracemalloc.get_traced_memory()
    report["faiss_index"] = {
        "current_mb": current / 1024 / 1024,
        "peak_mb": peak / 1024 / 1024,
        "embedding_dimensions": faiss_index.d,
        "num_vectors": faiss_index.ntotal,
    }
    tracemalloc.stop()

    # 4. Total
    report["total_rss_mb"] = process.memory_info().rss / 1024 / 1024
    report["recommendation"] = {
        "docker_memory_limit": f"{int(report['total_rss_mb'] * 1.5)}m",
        "docker_memory_reservation": f"{int(report['total_rss_mb'] * 1.2)}m",
    }

    output_path = Path("profiling_report.json")
    output_path.write_text(json.dumps(report, indent=2))
    print(f"\n=== Memory Profile Report ===")
    print(f"Pandas DataFrames: {report['pandas_dataframes']['current_mb']:.0f} MB")
    print(f"BM25 Index:        {report['bm25_index']['current_mb']:.0f} MB")
    print(f"FAISS Index:       {report['faiss_index']['current_mb']:.0f} MB")
    print(f"Total RSS:         {report['total_rss_mb']:.0f} MB")
    print(f"Docker limit:      {report['recommendation']['docker_memory_limit']}")
    print(f"Saved to: {output_path}")
    return report
```

**Docker Compose uses profiling output:**
```yaml
# docker-compose.yml — memory limits derived from profiling
services:
  terra-mcp:
    build: ./terra-mcp
    deploy:
      resources:
        limits:
          memory: ${MCP_MEMORY_LIMIT:-4g}    # default 4GB, override from profiling
        reservations:
          memory: ${MCP_MEMORY_RESERVATION:-3g}
```

### Coverage Targets

| Layer | Line Coverage | Mutation Kill Rate | Tool |
|-------|-------------|-------------------|------|
| MCP server (Python) | ≥ 85% | ≥ 65% | pytest-cov + mutmut |
| Backend domain (`terra-core`) | ≥ 90% | ≥ 70% | JaCoCo + PIT |
| Backend infrastructure (unit) | ≥ 75% | — | JaCoCo |
| Integration tests | At least 1 per critical path | — | — |
| Live LLM tests | 30 golden dataset entries | — | Weekly GitHub Action |

---

## Production-Ready Features

| Feature | Implementation |
|---------|---------------|
| **Multi-agent coordination** | Supervisor + DataRetrieval + AnalysisSynthesis agents with configurable budgets |
| **Agent guardrails** | Configurable loop limits, token budgets, timeouts (not hardcoded) |
| **Streaming responses** | SSE endpoint with tool-progress events for real-time UX |
| **Resilience** | `@Retryable(3×, 500ms backoff)` on `AgentPort`, circuit breaker on MCP HTTP calls |
| **Rate limiting** | Per-user Bucket4j rate limiting (20 queries/min default) |
| **Observability** | Micrometer timers on agent/tool calls, OTel traces to Grafana Tempo, tool-call chain logging |
| **Structured logging** | Logback JSON with traceId/conversationId correlation |
| **Multi-LLM support** | `@ConditionalOnProperty` for OpenAI / Anthropic / Ollama |
| **Cross-provider eval** | Generator (OpenAI) ≠ Evaluator (Anthropic), enforced at config level |
| **Conversation persistence** | Configurable scope: messages-only or full (with tool call details) |
| **Data quality pipeline** | Deduplication, null handling, normalization, quality reporting |
| **Index caching** | Pre-computed FAISS + BM25 indices cached to disk; ~2s startup after first run |
| **Pluggable data sources** | `BaseLoader` interface; add new CSVs without code changes |
| **API contract first** | OpenAPI 3.1 spec drives code generation and documentation |
| **CORS config** | `CorsConfig` for React frontend on port 3000 |
| **Health checks** | Spring Actuator + MCP server `/health` with per-source status |
| **Architecture enforcement** | ArchUnit (5 hexagonal boundary rules) |
| **Mutation testing** | PIT (Java, ≥70% kill rate on domain) + mutmut (Python, ≥65% kill rate) from Phase 1 onward |
| **Live LLM tests** | Weekly GitHub Action against real APIs; validates answer quality, not just pipeline |
| **Performance tests** | Gatling load tests (p50 ≤ 3s simple, p95 ≤ 12s complex, ≥10 req/s throughput) |
| **Memory profiling** | Mandatory Phase 1 deliverable; drives Docker resource limits |
| **Docker Compose** | All services orchestrated: `terra-mcp`, `backend`, `frontend`, `chromadb` (opt) |

---

## Implementation Phases (Kanban)

### Phase 1 — Data Foundation (MCP Server)
1. Download EOSDIS CSV and NOAA CSV, add to `terra-mcp/data/`
2. Build `BaseLoader` interface and `eosdis_loader.py`, `noaa_loader.py` with column normalization
3. Build `DataNormalizer` (country name → ISO 3166, null handling rules)
4. Build `CrossSourceDeduplicator` (fuzzy matching via rapidfuzz)
5. Build `DisasterRepository` (unified pandas wrapper with quality report)
6. Implement `disaster_query.py` and `disaster_stats.py` tools with Pydantic validation
7. Add `live_events.py` with NASA EONET client (httpx async, graceful fallback)
8. Build `HierarchicalChunker` with adaptive strategy for short/medium/long records
9. Build `HybridSearchEngine` (BM25 + FAISS + configurable RRF)
10. Build `IndexCache` (persist indices to disk, hash-based invalidation)
11. FastMCP server entry point with all tools registered
12. pytest suite: unit tests for all tools + dedup + chunker + search; integration tests for full pipeline
13. **Mandatory memory profiling**: run `memory_profile.py` against full dataset, document results, set Docker memory limits
14. Configure mutmut and run first mutation testing pass (target ≥65% kill rate)
15. Dockerfile for MCP server (with memory limits from profiling)

### Phase 2 — Backend Skeleton
16. Create OpenAPI 3.1 spec (`terra-query-api.yaml`) — contract first
17. Multi-module Gradle project setup (`terra-core`, `terra-infrastructure`)
18. Domain models and port interfaces (ChatMessage, Conversation, AgentResponse, ToolCallRecord)
19. `ChatService` and `ConversationService` (domain, zero Spring)
20. `AgentGuardrailsConfig` — all limits externalized to `application.yml`
21. JPA adapters + H2 config + configurable persistence scope
22. ArchUnit tests enforcing hexagonal boundaries
23. Configure PIT mutation testing plugin (target ≥70% kill rate on domain layer)

### Phase 3 — Multi-Agent Integration
24. `McpClientConfig` — connect to terra-mcp via Spring AI
25. `DataRetrievalAgent` — ChatClient with data-fetching MCP tools + focused system prompt
26. `AnalysisSynthesisAgent` — ChatClient with RAG tool + analysis system prompt
27. `SupervisorAgentAdapter` — coordinates sub-agents, implements AgentPort
28. `AgentConfig` — provider-agnostic ChatModel factory (OpenAI / Anthropic / Ollama)
29. `ToolArgumentSanitizer` — lightweight Spring-side safety net
30. REST controllers + DTOs (generated from OpenAPI spec)
31. `SseStreamingAdapter` + SSE streaming endpoint
32. Integration tests: full agent loop with mocked MCP responses
33. **Establish performance baselines**: run MCP micro-benchmarks + initial latency measurements, document p50/p95 for simple and complex queries

### Phase 4 — Evaluation Framework
34. `EvalConfig` — enforce generator (OpenAI) ≠ evaluator (Anthropic) provider separation
35. Build golden dataset: 30 Q&A pairs via GPT-4o, verified against Wikipedia + EM-DAT
36. `EvaluationService` + `CrossProviderEvalAdapter` — RAGAS-style metrics
37. Nightly schedule + CI/CD canary trigger
38. Evaluation threshold enforcement (target + minimum acceptable)

### Phase 5 — Frontend
39. React 19 app scaffold (Vite + Tailwind CSS)
40. `ChatWindow`, `MessageBubble`, `SourceCitations` components
41. `ToolProgressIndicator` — shows agent activity during streaming
42. `useConversation` hook + `useSSE` hook + `chatApi.js`
43. Recharts charts: `TrendChart`, `DisasterBarChart`, `TypeBreakdown`
44. `ChoroplethMap` via react-simple-maps (color-blind-safe Viridis palette)
45. `useDisasterViz` hook — parses structured agent response into chart-ready arrays
46. Accessibility: ARIA labels, keyboard navigation, alt-text for charts

### Phase 6 — Production Hardening & Performance
47. Docker Compose with all services (`terra-mcp`, `backend`, `frontend`)
48. Observability: Micrometer + OTel config, tool-call chain metrics, agent loop depth histograms
49. Resilience decorators (`@Retryable`, rate limiting via Bucket4j, circuit breaker)
50. `application-prod.yml` (PostgreSQL, API keys from env, index cache volume mount)
51. MCP server security: localhost binding, shared API key header
52. **Gatling load tests**: full scenario suite (simple + complex queries, ramp-up + sustained load)
53. **Performance validation**: verify p50 ≤ 3s (simple), p95 ≤ 12s (complex), throughput ≥ 10 req/s
54. **GitHub Actions setup**: live LLM tests (weekly + manual), performance tests (weekly + manual), nightly mutation + eval
55. End-to-end smoke test: `docker compose up` → ask question → verify answer

---

## Key Technologies

| Layer | Technology |
|-------|-----------|
| MCP Server | Python 3.12, FastMCP 2.x, pandas, httpx, sentence-transformers (`bge-base-en-v1.5`), FAISS, rank-bm25, rapidfuzz, pydantic |
| Backend | Java 25, Spring Boot 4.x, Spring AI 2.0 |
| LLM (generator) | OpenAI gpt-4o-mini (default) / Ollama llama3.2 |
| LLM (evaluator) | Anthropic claude-haiku — always a different provider from generator |
| Hybrid search | BM25 (rank-bm25) + dense (FAISS) fused via configurable RRF |
| Chunking | Hierarchical: ≤128-token child chunks (search) + ≤512-token parents (LLM context), adaptive for short records |
| Embedding | `bge-base-en-v1.5` (default) / `nomic-embed-text` (Ollama), cached to disk |
| Vector Store | FAISS in-process (MCP server) + SimpleVectorStore/ChromaDB (backend) |
| Database | H2 (dev) / PostgreSQL (prod) |
| Testing | pytest + pytest-benchmark + mutmut · JUnit 5 + ArchUnit + MockMvc + WireMock + @SpringBootTest + PIT |
| Load testing | Gatling (JVM), pytest-benchmark (Python MCP) |
| Frontend | React 19, Vite, Tailwind CSS, Recharts, react-simple-maps |
| Infra | Docker Compose, Grafana stack (optional) |
| API | OpenAPI 3.1 (contract-first) |

---

## What Makes This Different from Previous Projects

### vs. daily-context-ai (agent design)
| Aspect | daily-context-ai (old) | TerraQuery (new) |
|--------|------------------------|-------------------|
| Flow control | Java if/else in `OrchestratorService` | LLM-driven multi-agent coordination |
| Agent autonomy | None — hardcoded dispatch | Full — each agent picks tools freely within its domain |
| Architecture | Single agent pretending to be multiple | Specialized sub-agents with clear responsibilities |
| Tool calling | Single tool call per agent | Multi-turn iterative loop with configurable budget |
| Tool descriptions | Minimal | Rich `Annotated[type, "..."]` docstrings + Pydantic validation |
| Agent loop | classify → call once → synthesize | think → act → observe → loop → synthesize |
| Guardrails | None | Configurable loop limits, token budgets, timeouts |

### vs. rag-report-analyzer (RAG quality)
| Aspect | rag-report-analyzer (old) | TerraQuery (new) |
|--------|--------------------------|-------------------|
| Search strategy | Vector search + metadata WHERE filter (called "hybrid") | True hybrid: BM25 + dense vector + RRF score fusion |
| RRF parameters | N/A | A/B testable via environment variables |
| Chunking | Flat 512-token chunks | Hierarchical: adaptive child (≤128) + parent (≤512), handles short records |
| Evaluation bias | Same LLM generates and judges answers | Generator and evaluator are always different providers |
| Evaluation schedule | Ad-hoc | Nightly automated + CI/CD canary + on-data-change |
| Evaluation baselines | None | Target thresholds defined (precision ≥0.80, faithfulness ≥0.85, etc.) |
| Integration tests | Unit tests + ArchUnit only | Full pipeline `@SpringBootTest` + MCP integration tests |
| Mutation testing | None | PIT (Java) + mutmut (Python) from Phase 1 |
| Live LLM validation | None | Weekly GitHub Action against real APIs |
| Performance testing | None | Gatling load tests + pytest-benchmark with defined latency targets |
| Memory profiling | None | Mandatory Phase 1 deliverable with Docker limit derivation |
| Data quality | Assumed clean CSVs | Deduplication, null handling, normalization, quality reporting |
| API design | Code-first | OpenAPI 3.1 spec first |
| Streaming | Synchronous only | SSE with tool-progress events |
