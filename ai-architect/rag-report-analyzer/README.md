# RAG Financial Report Analyzer

A learning-oriented Retrieval-Augmented Generation (RAG) project for analyzing and predicting from
SEC financial reports (10-K / 10-Q PDFs). Built with Java 25, Spring Boot 4.0.3, and Spring AI 2.0.x.

## Architecture

Hexagonal (Ports & Adapters), enforced by ArchUnit.

```
domain/
  model/          — value objects: FinancialMetrics, FinancialOutlook, eval/*
  port/in/        — inbound ports (use cases / facades)
  port/out/       — outbound ports (repository, LLM, vector store, eval)
  service/        — domain services implementing inbound ports

infrastructure/
  adapter/in/rest/     — REST controllers
  adapter/out/ai/      — Spring AI ChatClient + EmbeddingModel adapters
  adapter/out/vectorstore/ — SimpleVectorStore / ChromaDB adapters
  adapter/out/persistence/ — JPA adapters (H2 / PostgreSQL)
  adapter/out/prediction/  — prediction strategy implementations
  adapter/out/eval/        — LLM-as-judge + golden dataset loader
  config/          — Spring @Configuration (AI, VectorStore)
  props/           — @ConfigurationProperties
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
# Edit .env

docker compose up -d
```

Services:
- Backend: http://localhost:8080
- Frontend: http://localhost:3000
- ChromaDB: http://localhost:8000 (when `VECTORSTORE_TYPE=chroma`)

## Configuration

All configuration is via environment variables (`.env` file, see `.env.example`):

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
