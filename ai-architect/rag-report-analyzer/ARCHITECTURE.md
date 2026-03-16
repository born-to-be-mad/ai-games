# RAG Financial Report Analyzer — Architecture

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [RAG Q&A Sequence Diagram](#rag-qa-sequence-diagram)
3. [STRIDE Threat Model](#stride-threat-model)

---

## Architecture Overview

```mermaid
graph TD
    subgraph Client["Client Layer"]
        U(["👤 User"])
    end

    subgraph FE["Frontend  ·  React 18 / TypeScript 5 / Nginx  :3000"]
        RA["ReportUploader.tsx"]
        QI["QaInterface.tsx"]
        MD["MetricsDashboard.tsx"]
        KG["KnowledgeGraph.tsx  (D3 v7)"]
        PP["PredictionPanel.tsx"]
    end

    subgraph BE["Backend  ·  Spring Boot 4.0.3 / Spring AI 2.0.0-M2  :8080"]
        subgraph IN["Inbound Adapters (REST)"]
            QC["QaController\nGET /api/v1/qa"]
            MC["MetricsController\nPOST /api/v1/metrics/extract\nGET  /api/v1/metrics/{ticker}/{year}/{quarter}\nGET  /api/v1/metrics/graph/{ticker}"]
            AC["AnalysisController\nGET /api/v1/analysis/{ticker}"]
            EC["EvaluationController\nPOST /api/v1/eval/run"]
        end

        subgraph DOMAIN["Domain (Hexagon Core)"]
            QAS["FinancialQaService\n@Cacheable(qa-answers · 30 min)"]
            MES["MetricsExtractionService"]
            PS["PredictionService\nNARRATIVE · LR · HYBRID"]
            ES["EvaluationService\nRAGAS-inspired"]
        end

        subgraph OUT["Outbound Adapters"]
            VSA["VectorStoreAdapter\nSimple (in-memory) │ Chroma (HTTP)\nTimer(vector.store.operation)"]
            LMA["SpringAiLanguageAdapter\n@Retryable(3×, 1s→2s→4s)\nTimer(llm.call.duration)"]
            MEA["MetricsExtractionAdapter\n@Retryable · ChatClient.entity()"]
            EVA["LlmEvalJudgeAdapter\n@Retryable · RAGAS judge"]
            RRA["ReportRepositoryAdapter\nJPA · H2 (dev) / PostgreSQL (prod)"]
            PDFA["PdfDocumentReaderAdapter\nPagePdfDocumentReader\n512-token chunks / 64 overlap"]
        end
    end

    subgraph EXT["External Services"]
        CHROMA[("ChromaDB  :8000\ncollection: rag-reports")]
        LLM["LLM APIs\nOpenAI gpt-4o-mini\nAnthropic claude-sonnet-4-6\nOllama llama3.2  :11434"]
        DB[("H2 (dev)\nPostgreSQL :5432 (--profile postgres)")]
    end

    subgraph OBS["Observability Stack  ·  --profile observability"]
        PROM["Prometheus  :9090\n/actuator/prometheus"]
        TEMPO["Grafana Tempo  :3200\nOTLP gRPC  :4317"]
        LOKI["Grafana Loki  :3100\nLoki4j JSON logs + traceId/spanId"]
        GRAF["Grafana  :3001\n11-panel dashboard\nadmin / admin"]
    end

    U -->|"HTTP :3000"| FE
    FE -->|"/api/* proxy"| IN

    QC --> QAS
    MC --> MES
    AC --> PS
    EC --> ES

    QAS --> VSA
    QAS --> LMA
    MES --> VSA
    MES --> MEA
    PS --> RRA
    ES --> VSA
    ES --> EVA
    ES --> LMA

    VSA -->|"HTTP REST"| CHROMA
    LMA -->|"HTTPS"| LLM
    MEA -->|"HTTPS"| LLM
    EVA -->|"HTTPS"| LLM
    RRA --> DB
    PDFA -->|"FileSystemResource"| VSA

    BE -->|"scrape :8080/actuator/prometheus"| PROM
    BE -->|"OTLP gRPC"| TEMPO
    BE -->|"Loki4j HTTP push"| LOKI
    PROM --> GRAF
    TEMPO --> GRAF
    LOKI --> GRAF
```

**Key architectural decisions:**
- **Hexagonal (Ports & Ports)** — domain core has zero framework dependencies; ArchUnit enforces boundaries
- **Provider-agnostic LLM** — single `AI_PROVIDER` env var switches OpenAI / Anthropic / Ollama
- **Vector store swap** — `@ConditionalOnProperty` selects `SimpleVectorStore` (dev) or ChromaDB (prod) with zero domain changes
- **Resilience** — `@Retryable` (3×, exponential backoff) on all LLM adapters; Caffeine cache on Q&A answers
- **Full observability** — Micrometer (metrics) + OTel OTLP (traces) + Loki4j (logs) → Grafana unified view

---

## RAG Q&A Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant FE  as Frontend (React/Nginx)
    participant QC  as QaController
    participant QS  as FinancialQaService
    participant CC  as Caffeine Cache
    participant VS  as ChromaVectorStoreAdapter
    participant CH  as ChromaDB
    participant LA  as SpringAiLanguageAdapter
    participant LLM as LLM API (OpenAI / Anthropic / Ollama)
    participant MT  as Micrometer / Tempo

    User ->> FE: Submit question + ticker + year
    FE  ->>+ QC: GET /api/v1/qa?question=...&ticker=NVDA&year=2025

    QC  ->>+ QS: ask(question, ticker, year)
    QS  ->>  CC: lookup key = "NVDA:2025:<question>"

    alt Cache HIT
        CC -->> QS: QaAnswer (cached, TTL 30 min)
    else Cache MISS
        QS  ->>  MT: start span "rag.qa"
        QS  ->>+ VS: similaritySearch(question, topK=5,\n  filter={ticker=NVDA, year=2025})

        VS  ->> MT: Timer.start("vector.store.operation", op=search_filtered)
        VS  ->>+ CH: POST /api/v2/collections/rag-reports/query\n  {embedding, n_results=5, where={ticker,year}}
        CH -->>- VS: top-5 document chunks + metadata
        VS  ->> MT: Timer.stop → record duration
        VS -->>- QS: List<Document>

        QS  ->>+ LA: answer(question, contextChunks)
        LA  ->> MT: Timer.start("llm.call.duration", provider, op=qa)
        LA  ->>+ LLM: ChatClient.call()\n  system=qa-system.st + context chunks\n  model=gpt-4o-mini / claude-sonnet-4-6 / llama3.2

        note over LA,LLM: @Retryable — up to 3 attempts<br/>backoff: 1 s → 2 s → 4 s

        LLM -->>- LA: generated answer text
        LA  ->> MT: Timer.stop + OTel span export → Tempo
        LA -->>- QS: String answer

        QS  ->>  CC: put("NVDA:2025:<question>", QaAnswer)
        QS -->>- QC: QaAnswer { answer, sources: [chunkId, text, pageNumber] }
    end

    QC -->>- FE: 200 OK  { answer, sources }
    FE -->>  User: Display answer card + collapsible source chunks
```

---

## STRIDE Threat Model

### System Boundary

The analysis covers: REST API, frontend proxy, backend services, vector store (ChromaDB), LLM adapters, database, and observability stack — all within the Docker Compose network.

---

### S — Spoofing

| # | Threat | Asset | Risk | Mitigation |
|---|--------|-------|------|------------|
| S1 | Any unauthenticated client can call all REST endpoints | All `/api/v1/*` | **High** | Add Spring Security with API-key or JWT bearer authentication |
| S2 | No service-to-service auth in Docker network — ChromaDB and PostgreSQL accept any connection | ChromaDB, PostgreSQL | **Medium** | Enable ChromaDB token auth; use PostgreSQL `pg_hba.conf` host restrictions |
| S3 | Grafana default credentials (`admin/admin`) — anyone can log in | Observability dashboards | **High** | Force password change on first login; use `GF_SECURITY_ADMIN_PASSWORD` env var |
| S4 | Ollama API has no authentication — any process on the Docker network can call it | Local LLM (Ollama) | **Low** (internal only) | Restrict Ollama to Docker-internal network; do not expose port 11434 externally |

---

### T — Tampering

| # | Threat | Asset | Risk | Mitigation |
|---|--------|-------|------|------------|
| T1 | ChromaDB has no auth → embeddings in `rag-reports` collection can be overwritten or poisoned | Vector store data | **High** | Enable ChromaDB `CHROMA_SERVER_AUTHN_CREDENTIALS`; restrict port 8000 to Docker-internal only |
| T2 | No input validation/sanitisation on `question`, `ticker`, `year` parameters → prompt injection via crafted question | LLM prompt | **High** | Validate and sanitise inputs; consider a prompt-injection guard layer |
| T3 | H2 web console enabled (`/h2-console`) in default profile — in-memory DB fully accessible with no auth | Financial metrics (H2) | **Medium** (dev only) | Disable H2 console in non-dev profiles; gate with Spring Security |
| T4 | `@Cacheable` stores answers keyed by `ticker:year:question`; a poisoned answer persists 30 min | Q&A cache | **Medium** | Add cache invalidation endpoint (admin-only); use signed/validated LLM responses |
| T5 | PDF ingestion reads from arbitrary file path (`SAMPLE_PDF_PATH`) with no path-traversal guard | Host filesystem | **Medium** | Validate and canonicalise the file path; restrict to a whitelist directory |

---

### R — Repudiation

| # | Threat | Asset | Risk | Mitigation |
|---|--------|-------|------|------------|
| R1 | No user identity — no authentication means all requests are anonymous; impossible to attribute actions | Audit log | **High** | Introduce user identity (JWT/session); log user ID in every structured log event |
| R2 | Eval matrix (`POST /api/v1/eval/run/matrix`) and ingestion can be triggered without trace to caller | Audit log, LLM budget | **Medium** | Log caller IP + timestamp + operation; protect write/compute endpoints behind auth |
| R3 | Loki captures logs with `traceId`/`spanId` but no user context — trace is incomplete for non-repudiation | Loki logs | **Low** | Add MDC `userId` field once authentication is in place |

---

### I — Information Disclosure

| # | Threat | Asset | Risk | Mitigation |
|---|--------|-------|------|------------|
| I1 | LLM API keys (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`) in environment variables — exposed via `docker inspect` or leaked logs | API credentials | **Critical** | Use Docker secrets or a vault (HashiCorp Vault / AWS Secrets Manager); never log env vars |
| I2 | Actuator endpoints `/metrics`, `/prometheus`, `/caches` expose internal state (cache keys, JVM heap, LLM timing) | System internals | **Medium** | Restrict actuator to management port + require auth; set `show-details: never` |
| I3 | Q&A response includes raw `sources` (document chunks with full text and page numbers) — proprietary PDF content exposed to any caller | PDF content (10-K data) | **Medium** | Auth-gate the API; consider redacting or summarising source chunks |
| I4 | H2 console (`/h2-console`) allows full SQL read/write with no credentials | Financial metrics DB | **Medium** | Disabled automatically in postgres profile; ensure never exposed in staging/prod |
| I5 | Stack traces may appear in error responses — expose internal class names and library versions | Implementation details | **Low** | Configure `server.error.include-stacktrace=never` in production |
| I6 | ChromaDB port 8000 and Grafana port 3001 exposed on `0.0.0.0` in docker-compose | Vector data, dashboards | **Medium** | Bind to `127.0.0.1` or use a reverse proxy with TLS; do not expose on public interfaces |

---

### D — Denial of Service

| # | Threat | Asset | Risk | Mitigation |
|---|--------|-------|------|------------|
| D1 | No rate limiting on any endpoint — burst of Q&A requests drains OpenAI/Anthropic API credits | LLM budget | **High** | Add rate limiting (Spring Gateway / nginx `limit_req`); set OpenAI usage limits |
| D2 | `POST /api/v1/eval/run/matrix` runs 3 full RAGAS evaluations (≈60 LLM calls) — a single request is extremely expensive | LLM budget, latency | **High** | Protect behind admin auth; add request queuing; limit to one concurrent run |
| D3 | `@Retryable` (3×) amplifies failures — a misconfigured LLM key sends 3× the failing requests before raising `LlmCallException` | LLM API rate limits | **Medium** | Add circuit breaker (Resilience4j); exponential backoff already in place |
| D4 | No max payload / request size limit — large or malformed PDFs can exhaust heap during chunking | JVM heap | **Medium** | Set `spring.servlet.multipart.max-file-size` and `max-request-size`; add PDF validation |
| D5 | ChromaDB collection has no size cap — unbounded ingestion can fill disk | Disk / ChromaDB | **Low** | Set ChromaDB collection limits; add disk-usage alerts in Grafana |

---

### E — Elevation of Privilege

| # | Threat | Asset | Risk | Mitigation |
|---|--------|-------|------|------------|
| E1 | H2 console (`/h2-console`) is fully open in default profile — any visitor can run arbitrary SQL | Database | **High** | Disable via `spring.h2.console.enabled=false` in non-dev profiles; Spring Security if kept |
| E2 | Actuator `/caches` endpoint allows clearing the Q&A Caffeine cache without authentication | Application state | **Medium** | Require `ACTUATOR_ROLE` to access write actuator endpoints |
| E3 | No RBAC — any user can trigger costly operations: metrics extraction, eval runs, cache inspection | Compute / LLM budget | **Medium** | Introduce roles: `READER` (Q&A), `ANALYST` (extract/predict), `ADMIN` (eval, actuator) |
| E4 | Grafana `admin/admin` → full dashboard/datasource management, can reconfigure Prometheus/Loki/Tempo | Observability infra | **Medium** | Rotate credentials; create read-only viewer accounts for non-admin users |
| E5 | No network segmentation — all Docker services share `rag-network`; a compromised frontend container can reach ChromaDB or PostgreSQL directly | Internal services | **Medium** | Separate Docker networks per tier (frontend ↔ backend, backend ↔ db); use firewall rules |

---

### Risk Summary

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Spoofing | — | S1, S3 | S2 | S4 |
| Tampering | — | T1, T2 | T3, T4, T5 | — |
| Repudiation | — | R1 | R2 | R3 |
| Information Disclosure | I1 | — | I2, I3, I4, I6 | I5 |
| Denial of Service | — | D1, D2 | D3, D4 | D5 |
| Elevation of Privilege | — | E1 | E2, E3, E4, E5 | — |

> **This system is designed as a learning-oriented POC.** For production deployment, the Critical and High items above must be addressed before exposing the application to untrusted users or the public internet.
