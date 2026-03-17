# RAG Financial Report Analyzer

A learning-oriented Retrieval-Augmented Generation (RAG) project for analyzing and predicting from
SEC financial reports (10-K / 10-Q PDFs). Built with Java 25, Spring Boot 4.0.3, and Spring AI 2.0.x.

## Architecture

Hexagonal (Ports & Adapters), enforced by ArchUnit. See [`ARCHITECTURE.md`](ARCHITECTURE.md) for full
diagrams (component overview, RAG supply chain, Q&A sequence diagram, and STRIDE threat model).

```
domain/
  model/          — value objects: FinancialMetrics, FinancialOutlook, eval/*
  port/in/        — inbound ports (use cases / facades)
  port/out/       — outbound ports (repository, LLM, vector store, eval)
  service/        — domain services implementing inbound ports

infrastructure/
  adapter/in/rest/     — REST controllers (including IngestionController)
  adapter/in/runner/   — IngestionRunner (optional startup ingestion)
  adapter/out/ai/      — Spring AI ChatClient + EmbeddingModel adapters
  adapter/out/vectorstore/ — SimpleVectorStore / ChromaDB adapters
  adapter/out/persistence/ — JPA adapters (H2 / PostgreSQL)
  adapter/out/prediction/  — prediction strategy implementations
  adapter/out/eval/        — LLM-as-judge + golden dataset loader
  config/          — Spring @Configuration (AI, VectorStore, Resilience)
  props/           — @ConfigurationProperties (IngestionProperties, AiProviderProperties)
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| AI Framework | Spring AI 2.0.0-M2 |
| LLM providers | OpenAI (default), Anthropic, Ollama |
| Vector stores | SimpleVectorStore (dev) / ChromaDB (prod) |
| Embeddings | OpenAI `text-embedding-3-small` / Ollama `nomic-embed-text` |
| Persistence | H2 (dev) / PostgreSQL (prod) |
| Frontend | React 18, TypeScript 5, Vite, Tailwind CSS, TanStack Query, D3 v7 |
| Observability | Prometheus, Grafana, Grafana Tempo (traces), Grafana Loki (logs) |
| Build | Gradle 9 (Kotlin DSL) |
| Architecture tests | ArchUnit 1.4.1 |

## Quick Start

### Prerequisites
- Java 25
- Docker + Docker Compose (for ChromaDB and full stack)
- An OpenAI API key (or configure another provider)

### Local (SimpleVectorStore, in-memory)
```bash
cp .env.example .env
# Edit .env — set AI_PROVIDER and the corresponding API key

cd backend
./gradlew bootRun
```

### Full Stack (Docker Compose)
```bash
cp .env.example .env
# Edit .env — set AI_PROVIDER and the corresponding API key

docker compose up -d
```

Core services:

| Service | URL | Notes |
|---------|-----|-------|
| Frontend | http://localhost:3000 | React SPA (Nginx) |
| Backend | http://localhost:8080 | Spring Boot API |
| ChromaDB | http://localhost:8000 | Persistent vector store |

### Docker Compose Profiles

Optional services are activated via `--profile`:

```bash
# PostgreSQL (replaces H2) — also set SPRING_PROFILES_ACTIVE=postgres in .env
docker compose --profile postgres up -d

# Ollama (local LLM) — then set AI_PROVIDER=ollama in .env
docker compose --profile ollama up -d

# Observability (Prometheus + Tempo + Loki + Grafana)
docker compose --profile observability up -d

# Combine profiles
docker compose --profile postgres --profile observability up -d
```

## Configuration

All configuration is via environment variables (`.env` file, see `.env.example`):

**AI & Vector Store**

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_PROVIDER` | `openai` | Active LLM provider: `openai` \| `anthropic` \| `ollama` |
| `OPENAI_API_KEY` | — | Required when `AI_PROVIDER=openai` |
| `ANTHROPIC_API_KEY` | — | Required when `AI_PROVIDER=anthropic` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Required when `AI_PROVIDER=ollama` |
| `VECTORSTORE_TYPE` | `simple` | `simple` (in-memory) \| `chroma` (persistent) |
| `CHROMA_HOST` | `localhost` | ChromaDB host (used when `VECTORSTORE_TYPE=chroma`) |
| `CHROMA_PORT` | `8000` | ChromaDB port |
| `PREDICTION_MODE` | `NARRATIVE_PREDICTION` | Default prediction strategy |

**Startup Ingestion**

| Variable | Default | Description |
|----------|---------|-------------|
| `INGEST_ON_START` | `false` | Auto-ingest a PDF when the app starts (set `true` to enable) |
| `SAMPLE_PDF_PATH` | `../tmp/NVIDIA-2025-Annual-Report.pdf` | Path to the PDF to ingest on startup |
| `INGEST_TICKER` | `NVDA` | Ticker for the startup report |
| `INGEST_YEAR` | `2025` | Fiscal year for the startup report |
| `INGEST_QUARTER` | `Annual` | Quarter for the startup report (`Annual`, `Q1`–`Q4`) |

**PostgreSQL** (activate with `--profile postgres` + `SPRING_PROFILES_ACTIVE=postgres`)

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `ragdb` | Database name |
| `POSTGRES_USER` | `raguser` | Database user |
| `POSTGRES_PASSWORD` | `change-me` | Database password |

## Observability

Activate the full observability stack with:

```bash
docker compose --profile observability up -d
```

| Service | URL | Purpose |
|---------|-----|---------|
| Grafana | http://localhost:3001 | Unified dashboards (login: `admin` / `admin`) |
| Prometheus | http://localhost:9090 | Metrics scraping from `/actuator/prometheus` |
| Grafana Tempo | http://localhost:3200 | Distributed traces (OTLP gRPC on `:4317`) |
| Grafana Loki | http://localhost:3100 | Structured logs (pushed via Loki4j with `traceId`/`spanId`) |

A pre-built **11-panel Grafana dashboard** is auto-provisioned at startup and includes:
- JVM metrics (heap, GC, threads)
- LLM call duration and retry rates (`llm.call.duration`)
- Vector store operation timing (`vector.store.operation`)
- Cache hit/miss ratios (`qa-answers`)
- Trace exploration via Tempo

**Actuator endpoints** exposed at http://localhost:8080/actuator:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health (details when authorized) |
| `/actuator/prometheus` | Prometheus-format metrics scrape target |
| `/actuator/metrics` | Metrics browser (e.g. `/actuator/metrics/llm.call.duration`) |
| `/actuator/caches` | Cache statistics (Caffeine `qa-answers`) |
| `/actuator/info` | Application info |

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/ingest` | Ingest a PDF report (multipart: `file`, `ticker`, `year`, `quarter`) |
| `GET` | `/api/v1/qa?question=...&ticker=...&year=...` | Ask a question about a report |
| `POST` | `/api/v1/metrics/extract?ticker=...&year=...&quarter=...` | Extract structured metrics |
| `GET` | `/api/v1/metrics/{ticker}/{year}/{quarter}` | Retrieve stored metrics |
| `GET` | `/api/v1/metrics/graph/{ticker}` | D3-ready nodes/edges for knowledge graph |
| `GET` | `/api/v1/analysis/{ticker}?mode=HYBRID` | Financial prediction (`NARRATIVE_PREDICTION` \| `LINEAR_REGRESSION` \| `HYBRID`) |
| `POST` | `/api/v1/eval/run?topK=5` | Run RAG evaluation against golden dataset |
| `POST` | `/api/v1/eval/run/matrix` | Matrix evaluation: topK = 3, 5, 10 |

## Documentation

| Document | Description |
|----------|-------------|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Component diagram, RAG supply chain, Q&A sequence diagram, STRIDE threat model |
| [`EVALUATION.md`](EVALUATION.md) | RAGAS-style metrics, how to run evaluation, topK / chunk-size comparison matrix |

## Evaluation Dataset

See [`EVALUATION.md`](EVALUATION.md) for metrics, methodology, and comparison matrix.

### Golden Dataset Sources

The evaluation dataset (`backend/src/main/resources/eval/golden-dataset.json`) contains **30 manually
verified Q&A pairs** drawn from SEC 10-K filings. **These are not LLM-generated.**

| Company | Ticker | Filing | Period | Items | SEC EDGAR |
|---------|--------|--------|--------|-------|-----------|
| NVIDIA Corporation | NVDA | 10-K | FY2025 (ended Jan 26 2025) | 20 | [CIK 0001045810](https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=0001045810&type=10-K&dateb=&owner=include&count=5) |
| EPAM Systems, Inc. | EPAM | 10-K | FY2023 (ended Dec 31 2023) | 10 | [CIK 0001352010](https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=0001352010&type=10-K&dateb=&owner=include&count=5) |

To run the evaluation, download the PDF filings from SEC EDGAR and ingest them via `POST /api/v1/ingest`
before calling `POST /api/v1/eval/run`.

**NVIDIA FY2025 10-K highlights covered:**
revenue ($130.5B), Data Center ($115.2B, +142% YoY), net income ($72.9B), EPS ($2.94),
operating income ($93.3B), R&D ($8.7B), operating cash flow ($64.1B), Blackwell architecture,
CUDA ecosystem, export control risks, Gaming/Auto/Professional Visualization segments.

**EPAM FY2023 10-K highlights covered:**
revenue ($4.69B, -3.5% YoY), net income ($686.9M), EPS (~$11.85), headcount (~52,150),
Russia-Ukraine war impact, geographic delivery diversification (India, Poland, Hungary), strategy.

## Running Tests

```bash
cd backend
./gradlew test              # all 39 unit + architecture tests
./gradlew test --info       # verbose output
```

Tests include:
- 5 ArchUnit rules (hexagonal boundary enforcement)
- Unit tests for all domain services (no Spring context required)
- Strategy and adapter unit tests
