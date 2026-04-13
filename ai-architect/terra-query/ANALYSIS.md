# TerraQuery (Issue #33) — Detailed Analysis & Recommendations

## 1. Executive Summary

Issue #33 proposes **TerraQuery** (formerly "disaster-chatbot") — a production-ready multi-agent chatbot that answers natural disaster queries. It synthesizes lessons from two prior projects (`daily-context-ai` and `rag-report-analyzer`) into a system with true agentic behavior. The plan is ambitious and well-structured, explicitly addressing every piece of reviewer feedback from both predecessors. The architecture is sound at the macro level — hexagonal design, multi-agent coordination, hybrid RAG, cross-provider evaluation — and has been refined with resolved decisions on all 19 originally identified open questions.

This document captures the initial analysis, the decisions made, and the final state of the plan.

---

## 2. What the Plan Gets Right (Strengths)

### 2.1 Genuine Course Correction on Agent Design

The plan correctly identifies the fundamental flaw in `daily-context-ai`: the LLM classified intent but Java code controlled flow. The new design uses a **multi-agent architecture** with specialized sub-agents (DataRetrievalAgent + AnalysisSynthesisAgent) coordinated by a SupervisorAgent. Each agent has full tool access within its domain via Spring AI's `ChatClient.defaultTools()` and the framework handles the think→act→observe loop automatically. This is the correct agentic pattern — the LLMs are the orchestrators, not classifiers feeding into if/else branches.

### 2.2 Honest Hybrid Search

The plan replaces the mislabeled "hybrid search" (which was actually vector search + a metadata WHERE clause) with a genuine BM25 + dense vector retrieval pipeline fused via Reciprocal Rank Fusion (RRF). This directly addresses the reviewer's critique and follows the established pattern from papers like "Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods" (Cormack et al., 2009).

### 2.3 Hierarchical Chunking

Moving from flat 512-token chunks to a two-tier system (128-token children for precise embedding match, 512-token parents for LLM context) is a well-known improvement. It aligns with techniques used in LlamaIndex's `SentenceWindowNodeParser` and LangChain's `ParentDocumentRetriever`. The plan correctly explains *why* this matters and provides a clear implementation sketch.

### 2.4 Cross-Provider Evaluation

Separating generator (OpenAI) from evaluator (Anthropic) at the `@Configuration` level — making it structurally impossible to accidentally self-evaluate — is an elegant solution to the LLM-as-judge bias problem. This is a stronger guarantee than simply using different model names within the same provider.

### 2.5 Architecture Discipline

Carrying forward hexagonal architecture with ArchUnit enforcement from `rag-report-analyzer` is the right call. The 5-rule boundary check (domain has zero Spring imports, services depend only on port interfaces, etc.) prevents architecture erosion over time. The multi-module Gradle structure (`terra-core`, `terra-infrastructure`, `terra-frontend`) physically enforces these boundaries at compile time.

### 2.6 MCP Tool Descriptions

The tool definitions in the plan use rich `Annotated[type, "..."]` docstrings with accepted values, ranges, and usage guidance. This directly addresses the daily-context reviewer's recommendation to invest in proper tool and field descriptions per the FastMCP specification.

---

## 3. Gaps & Improvement Recommendations

### 3.1 Agent Safety & Guardrails (CRITICAL)

**Gap:** The plan gives the LLM unrestricted tool access with no guardrails. There is no mention of:
- Maximum tool call iterations (runaway loops)
- Token budget limits per conversation turn
- Input validation/sanitization on tool arguments
- Output content filtering
- Cost monitoring per query

**Recommendation:** Add an `AgentGuardrailsConfig` that wraps the ChatClient:
- Set `maxToolCalls(10)` — prevent infinite loops
- Set max token budget per turn (e.g., 8K output tokens)
- Add a `ToolArgumentValidator` interceptor that validates types, ranges, and injection attacks before passing to MCP tools
- Log all tool calls with arguments and results for audit trail
- Add a circuit breaker: if tool calls fail 3× in a row, return a graceful error instead of retrying forever

### 3.2 Data Quality & Engineering (HIGH)

**Gap:** The plan assumes CSV data is clean and focuses on loading/normalization. Real-world disaster datasets have significant quality issues:
- The EOSDIS dataset (1900–2021) has inconsistent country names, missing death tolls coded as 0 vs. null, duplicate events across sources, and varying date formats
- Merging EOSDIS + NOAA will produce duplicates (same event, different reporting)

**Recommendation:**
- Add a `DataQualityPipeline` stage between loading and indexing: deduplication (fuzzy matching on date + location + type), null handling strategy (distinguish "zero deaths" from "unknown"), country name normalization (map to ISO 3166-1 alpha-3 consistently)
- Add data quality metrics: % of records with missing fields, duplicate rate after merge, coverage gaps (years/regions with suspiciously few events)
- Consider adding the EM-DAT database as a validation reference (it's the gold standard for disaster statistics, maintained by CRED/UCLouvain)

### 3.3 Streaming & Real-Time UX (HIGH)

**Gap:** The plan uses synchronous `chatClient.prompt().call().content()` which blocks until the full response is generated. For complex multi-tool queries, this could mean 15–30 seconds of silence. The frontend wireframe shows no streaming or progress indicators for tool execution.

**Recommendation:**
- Use Spring AI's streaming API: `chatClient.prompt().stream()` with SSE (Server-Sent Events) to the frontend
- Emit intermediate events: `{"type": "tool_call", "tool": "query_disasters", "status": "executing"}` so the UI can show "Searching historical database..." in real-time
- Add a `ToolCallProgressObserver` that hooks into Spring AI's tool execution lifecycle

### 3.4 Conversation Memory & Context Window Management (HIGH)

**Gap:** The `SpringAiAgentAdapter.execute()` passes full conversation history to the LLM. For long conversations, this will exceed context window limits. There's no mention of conversation summarization, sliding window, or history pruning.

**Recommendation:**
- Implement a `ConversationWindowStrategy` (port interface in domain):
  - `SlidingWindow` — keep last N messages
  - `SummarizingWindow` — compress older messages via LLM summarization
  - `HybridWindow` — last 5 messages verbatim + summary of older ones
- Add token counting before each LLM call; if history + system prompt + tools > 80% of context window, trigger compression

### 3.5 Structured Agent Output (MEDIUM)

**Gap:** The `AgentResponse` is a plain text string. The response JSON schema shows `toolsUsed` and `sources` fields, but the plan doesn't explain how these are extracted from the LLM's free-text response.

**Recommendation:**
- Use Spring AI's structured output with a response schema:
  ```java
  record AgentStructuredResponse(
      String answer,
      List<String> toolsUsed,
      List<SourceCitation> sources,
      Optional<ChartData> visualization
  )
  ```
- Alternatively, parse tool call metadata from Spring AI's `ChatResponse.getMetadata()` rather than relying on the LLM to self-report which tools it used

### 3.6 Rate Limiting & Cost Control (MEDIUM)

**Gap:** The plan mentions resilience decorators (`@Retryable`, circuit breaker) but nothing about rate limiting user requests or controlling LLM API costs.

**Recommendation:**
- Add per-user rate limiting (Bucket4j or similar): e.g., 20 queries/minute
- Add per-query cost estimation: count input/output tokens, log estimated cost
- Add a daily cost cap with graceful degradation (switch to cheaper model or return cached results)
- The daily-context project already implemented Bucket4j rate limiting — reuse that pattern

### 3.7 Caching Strategy (MEDIUM)

**Gap:** The plan states "no caching needed for live data" (EONET) but doesn't address caching for historical queries. Repeated questions about "deadliest earthquakes ever" will trigger the same tool calls and LLM inference every time.

**Recommendation:**
- Cache MCP tool responses for historical queries (stable data) with a TTL of 1 hour
- Cache at the semantic level: hash the normalized query + tool arguments as cache key
- Exclude EONET (live) responses from cache, or cache with a short TTL (5 minutes)

### 3.8 Security — MCP Server Exposure (MEDIUM)

**Gap:** The plan puts the MCP server in Docker but doesn't discuss access control. If the MCP server is exposed on a network port, any client could call the tools.

**Recommendation:**
- Run the MCP server on `localhost` only, not `0.0.0.0`
- Add a shared secret / API key header between the Spring Boot backend and MCP server
- Add input length limits on all tool parameters (prevent denial-of-service via huge queries)
- Sanitize all CSV-derived data against injection if it's ever rendered in HTML (XSS prevention)

### 3.9 Alternative Dataset Consideration (MEDIUM)

**Gap:** The EOSDIS Kaggle dataset (1900–2021) is 4 years stale. NOAA covers USA only. NASA EONET covers live events but with limited historical depth.

**Recommendation — consider these alternatives or additions:**
- **EM-DAT (CRED)**: The international gold standard for disaster data. Requires registration but is free for academic/research use. Covers 1900–present, globally, with standardized fields.
- **GDACS (Global Disaster Alert and Coordination System)**: Real-time alerts with severity scores and geographic impact areas. Could replace or complement EONET.
- **ReliefWeb API**: UN OCHA's disaster reporting database with structured event data and humanitarian impact. Free REST API, no registration needed.
- **FEMA Disaster Declarations (USA)**: Official US disaster declarations with financial assistance data.

### 3.10 Observability Depth (LOW)

**Gap:** The plan mentions Micrometer + OTel but doesn't describe what metrics matter for an agentic system.

**Recommendation — instrument these specifically:**
- **Tool call distribution**: which tools the LLM selects most often (reveals if tool descriptions are imbalanced)
- **Tool call chains**: common sequences of tool calls (reveals agentic reasoning patterns)
- **Retrieval quality**: distribution of RRF scores across queries (are hybrid results consistently better?)
- **Agent loop depth**: histogram of tool calls per query (1, 2, 3+) — catches runaway loops early
- **Latency breakdown**: time spent in LLM inference vs. tool execution vs. search — identifies bottlenecks

### 3.11 Error Handling & Graceful Degradation (LOW)

**Gap:** What happens when one data source is unavailable? If NOAA loader fails at startup, does the whole MCP server crash?

**Recommendation:**
- Each data loader should be independent and fault-tolerant: if EOSDIS loads but NOAA fails, the server starts with partial data and logs a warning
- Add a `/health` endpoint on the MCP server that reports which data sources are loaded and their record counts
- If the NASA EONET API is down, `get_live_events` should return a clear "Live event data temporarily unavailable" message rather than an error trace

### 3.12 Frontend Accessibility (LOW)

**Gap:** The frontend wireframe includes a choropleth map and charts but no mention of accessibility (ARIA labels, keyboard navigation, screen reader support, color-blind-safe palettes).

**Recommendation:**
- Use color-blind-safe palettes for all charts (e.g., Viridis instead of the Red sequential scale)
- Add ARIA labels to all interactive chart elements
- Ensure the chat interface is fully keyboard-navigable
- Add alt-text generation for chart visualizations

---

## 4. Project Name — DECIDED

**TerraQuery** (`terra-query`) — confirmed. The name evokes earth-related querying, is concise, memorable, and domain-relevant. It suggests both the data domain (terra = earth, natural disasters) and the interaction model (query). Module naming convention: `terra-mcp`, `terra-core`, `terra-infrastructure`, `terra-frontend`. Java package: `com.aiarchitect.terraquery`.

---

## 5. Resolved Questions (19/19)

All originally identified questions have been resolved and incorporated into the updated PLAN.md.

### Architecture & Design

| # | Question | Decision | Implementation in PLAN.md |
|---|----------|----------|--------------------------|
| 1 | Agent loop depth limit | Configurable, not hardcoded. Follows Spring AI `maxIterations` pattern. | `AgentGuardrailsConfig` with per-agent limits: supervisor=3, retrieval=8, analysis=4 (all in `application.yml`) |
| 2 | Multi-agent vs. single-agent | Multi-agent with specialized sub-agents | SupervisorAgent → DataRetrievalAgent (MCP data tools) + AnalysisSynthesisAgent (RAG + reasoning) |
| 3 | Tool argument validation | Defense-in-depth: MCP server (Pydantic) is authoritative; Spring side has lightweight sanitizer | Pydantic `BaseModel` validation in Python + `ToolArgumentSanitizer` interceptor on Spring side |
| 4 | Conversation persistence scope | Configurable via property | `persistence-scope: FULL | MESSAGES_ONLY` — FULL includes tool call arguments and raw results |

### Data & RAG

| # | Question | Decision | Implementation in PLAN.md |
|---|----------|----------|--------------------------|
| 5 | Dataset freshness | Pluggable architecture — inject new CSVs anytime via `BaseLoader` interface | `BaseLoader` ABC with `load()` → normalized DataFrame. Future sources: EM-DAT, GDACS, ReliefWeb, FEMA |
| 6 | Embedding model choice | `bge-base-en-v1.5` (default), `nomic-embed-text` for Ollama. Embedded once at startup, cached to disk. | Configurable via `terra-query.mcp.embedding-model`. `IndexCache` persists FAISS + BM25 to disk |
| 7 | RRF constant (k=60) | A/B testable via environment variables, defaults to k=60 from original paper | `RRFConfig` with `k`, `bm25_weight`, `vector_weight` loaded from env vars |
| 8 | Hierarchical chunk boundaries | Adaptive 3-tier strategy for short/medium/long records | Records < 32 tokens: self-parent. < 512 tokens: single parent, split children. ≥ 512: full hierarchy. Plus `_enrich_text()` for bare CSV rows |
| 9 | Deduplication across sources | Fuzzy matching via rapidfuzz on (date + location + type) | `CrossSourceDeduplicator` with 85% similarity threshold, 7-day date tolerance, merge preferring higher-detail source |

### Evaluation & Quality

| # | Question | Decision | Implementation in PLAN.md |
|---|----------|----------|--------------------------|
| 10 | Golden dataset provenance | LLM-generated (GPT-4o), verified against Wikipedia + EM-DAT by humans | 30 Q&A pairs in `eval/golden-dataset.json` with `verification_sources` field per entry |
| 11 | Evaluation frequency | Nightly (full dataset, automated) + CI/CD canary (10 questions, manual trigger) + on-data-change | `eval/eval-config.yml` with cron schedule, manual trigger, and data-hash-change trigger |
| 12 | Baseline comparison | Targets defined based on published RAGAS benchmarks | Context Precision ≥0.80, Context Recall ≥0.75, Faithfulness ≥0.85, Answer Relevance ≥0.80. Minimum acceptable: -0.10 from each target |

### Operations & Production

| # | Question | Decision | Implementation in PLAN.md |
|---|----------|----------|--------------------------|
| 13 | Startup time | Pre-computed indices cached to disk; ~2s startup after first run | `IndexCache` with MD5-based data hash invalidation. First run: 3–5 min. Subsequent: ~2s |
| 14 | Memory footprint | Mandatory profiling in Phase 1; Docker limits derived from results | `memory_profile.py` script with `tracemalloc` + `psutil`. Outputs per-component breakdown and Docker `deploy.resources.limits` values. Estimate: ~2–4 GB |
| 15 | Multi-tenancy | Single-user for now | No user isolation needed. Rate limiting is global, not per-user |
| 16 | API versioning | OpenAPI 3.1 spec first (contract-first) | `api-spec/terra-query-api.yaml` drives code generation and documentation |

### Testing

| # | Question | Decision | Implementation in PLAN.md |
|---|----------|----------|--------------------------|
| 17 | WireMock scope | Weekly live LLM test suite via dedicated GitHub Action (manual + scheduled) | `live-llm-tests.yml` — runs real API calls against golden dataset every Sunday + manual `workflow_dispatch`. `LiveLlmAnswerQualityTest.java` with `@Tag("live-llm")`, excluded from normal CI |
| 18 | Mutation testing | PIT + mutmut from Phase 1 onward (no waiting for coverage) | PIT configured in `build.gradle.kts` (≥70% kill rate on domain). mutmut in `pyproject.toml` (≥65% kill rate on tools/search). Both run nightly alongside eval |
| 19 | Performance/load testing | Fully planned with targets, tools, and GitHub Action | Gatling load tests: p50 ≤ 3s (simple), p95 ≤ 12s (complex), ≥10 req/s. MCP micro-benchmarks via pytest-benchmark. Baselines established in Phase 3, full load tests in Phase 6. `performance.yml` GitHub Action (weekly + manual) |

---

## 6. Summary: Priority Matrix (updated with resolution status)

| Priority | Recommendation | Status | Notes |
|----------|---------------|--------|-------|
| **P0** | Agent guardrails (loop limits, token budgets, input validation) | **ADDRESSED** | `AgentGuardrailsConfig` with configurable per-agent limits in PLAN.md |
| **P0** | Data quality pipeline (deduplication, null handling, normalization) | **ADDRESSED** | `CrossSourceDeduplicator`, `DataNormalizer`, quality report in PLAN.md |
| **P1** | Streaming responses + tool progress events | **ADDRESSED** | SSE endpoint with `ChatEvent` types (TOOL_CALL_START, ANSWER_CHUNK, etc.) in PLAN.md |
| **P1** | Conversation context window management | **ADDRESSED** | `ContextWindowStrategy` enum (SLIDING_WINDOW, SUMMARIZING, HYBRID) in guardrails config |
| **P1** | Structured agent output (extract tool metadata programmatically) | **ADDRESSED** | `ChatResponse` schema with `toolsUsed`, `sources`, `agentChain`, `visualization` fields |
| **P1** | Multi-agent architecture | **ADDRESSED** | Supervisor + DataRetrieval + AnalysisSynthesis agents with clear domain boundaries |
| **P2** | Rate limiting & cost control | **ADDRESSED** | `maxQueriesPerMinute` + `dailyCostCapUsd` in guardrails config |
| **P2** | Caching for historical queries | **PARTIAL** | Index caching done; query-level caching is a Phase 6 enhancement |
| **P2** | MCP server security (localhost binding, API key) | **ADDRESSED** | Phase 6 item: localhost binding + shared API key header |
| **P3** | Additional data sources (EM-DAT, GDACS, ReliefWeb) | **PLANNED** | Pluggable `BaseLoader` architecture enables future sources |
| **P3** | Agentic observability (tool call chains, loop depth histograms) | **ADDRESSED** | Phase 6 observability with tool-call chain metrics |
| **P3** | Performance & load testing | **ADDRESSED** | Gatling + pytest-benchmark with latency/throughput targets; baselines Phase 3, full tests Phase 6 |
| **P3** | Memory profiling | **ADDRESSED** | Mandatory Phase 1 deliverable; `memory_profile.py` → Docker resource limits |
| **P3** | Mutation testing | **ADDRESSED** | PIT + mutmut from Phase 1 onward (no waiting for coverage) |
| **P3** | Live LLM answer quality tests | **ADDRESSED** | Weekly GitHub Action + manual trigger via `workflow_dispatch` |
| **P3** | Frontend accessibility | **ADDRESSED** | Phase 5 includes ARIA labels, keyboard nav, Viridis color palette |

## 7. All Items Resolved

All 19 original questions plus the 4 follow-up items have been resolved and incorporated into PLAN.md:

| Item | Status | Key Decision |
|------|--------|--------------|
| Live LLM tests | **RESOLVED** | Weekly GitHub Action + manual trigger via `workflow_dispatch` |
| Mutation testing | **RESOLVED** | PIT + mutmut from Phase 1 onward, targets: 70% (Java), 65% (Python) |
| Performance testing | **RESOLVED** | Gatling + pytest-benchmark with concrete latency/throughput targets |
| Memory profiling | **RESOLVED** | Mandatory Phase 1 deliverable; Docker limits derived from profiling report |

No open items remain. The plan is ready for implementation.

---

*Analysis prepared: April 12, 2026. Updated with all decisions resolved: April 12, 2026. All 19+4 items closed.*
*Based on: GitHub Issue #33, PLAN.md, daily-context-ai codebase, rag-report-analyzer codebase, and reviewer feedback on both projects*
