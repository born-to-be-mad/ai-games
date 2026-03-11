# Daily Context AI

An AI-powered agent orchestrator application that answers questions about current weather and latest news using Spring AI, MCP servers, and multiple AI providers.

## Overview

Daily Context AI uses the Orchestrator-Workers pattern to intelligently route user queries to specialized agents:
- **Weather Agent**: Queries multiple weather providers (Open-Meteo, WeatherAPI, OpenWeatherMap)
- **News Agent**: Aggregates news from multiple sources (TheNewsAPI, GNews.io, NewsAPI)

## Features

- Multi-provider weather information (configurable per request)
- Multi-source news aggregation (configurable per request)
- AI provider flexibility (Ollama, OpenAI, Anthropic)
- Conversation history with H2 database persistence
- React web interface
- Docker Compose deployment

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| AI | Spring AI 2.0.0-M2 |
| Build | Gradle 9.2.1 + `java-library` |
| Database | H2 (file-based) |
| MCP Servers | Python 3.12, `mcp>=1.26.0` |
| Frontend | React |
| Deployment | Docker Compose |

## Architecture

### Orchestrator-Workers Pattern

```
User Query
    │
    ▼
OrchestratorService        ← LLM classifies intent → QueryIntent record
    │
    ├── virtual thread ──► WeatherAgent ──► Weather MCP tools (openmeteo / weatherapi / owm)
    │
    └── virtual thread ──► NewsAgent    ──► News MCP tools (thenewsapi / gnews / newsapi)
    │
    ▼
AgentCoordinationService   ← collects List<AgentResult>
    │
    ▼
OrchestratorService        ← LLM synthesizes final markdown response
```

Both agents run in parallel via `CompletableFuture.supplyAsync` on a `newVirtualThreadPerTaskExecutor`.
Tomcat and Spring's async executor also use virtual threads (`spring.threads.virtual.enabled=true`).

### Modules

| Module | Responsibility |
|---|---|
| `orchestrator-core` | Domain entities, JPA repositories, AI provider config, domain records, virtual thread config |
| `orchestrator-mcp` | MCP client wrappers (`WeatherMcpClient`, `NewsMcpClient`), `SyncMcpToolCallbackProvider` beans |
| `orchestrator-web` | Spring Boot app, agents (`WeatherAgent`, `NewsAgent`), orchestrator service, REST API |
| `orchestrator-frontend` | React web UI |

### Ports

| Port | Service |
|---|---|
| 8080 | Spring Boot Application |
| 8101 | Open-Meteo MCP Server |
| 8102 | News Aggregator MCP Server |
| 8103 | WeatherAPI MCP Server |
| 8104 | OpenWeatherMap MCP Server |
| 11434 | Ollama |

## Dependencies

### Spring AI Starters

Spring AI 2.0.0-M2 uses updated artifact naming (`spring-ai-starter-*`):

| Artifact | Purpose |
|---|---|
| `spring-ai-starter-model-ollama` | Ollama chat model |
| `spring-ai-starter-model-openai` | OpenAI chat model |
| `spring-ai-starter-model-anthropic` | Anthropic chat model |
| `spring-ai-starter-mcp-client` | MCP client (Streamable HTTP transport) |

### Other Key Libraries

- `spring-boot-starter-web` — REST API
- `spring-boot-starter-data-jpa` — persistence
- `spring-boot-starter-webflux` — reactive HTTP (required by MCP client)
- `spring-boot-starter-validation` — bean validation
- `h2` — embedded database
- `jackson-databind` — JSON serialization

## Setup

### 1. Environment variables

```bash
cp .env.example .env
# Fill in API keys for the providers you want to use
```

Required keys per service:

| Variable | Service | Required |
|---|---|---|
| `WEATHERAPI_KEY` | WeatherAPI MCP server | Optional |
| `OPENWEATHERMAP_KEY` | OpenWeatherMap MCP server | Optional |
| `THENEWSAPI_KEY` | News Aggregator (TheNewsAPI) | Optional |
| `GNEWS_KEY` | News Aggregator (GNews) | Optional |
| `NEWSAPI_KEY` | News Aggregator (NewsAPI) | Optional |
| `OPENAI_API_KEY` | Spring Boot app (OpenAI provider) | Optional |
| `ANTHROPIC_API_KEY` | Spring Boot app (Anthropic provider) | Optional |

Open-Meteo requires no key. Ollama runs locally. At least one news key needed for news results.

### API Key Acquisition Guide

#### No key required

| Service | Notes |
|---|---|
| **Open-Meteo** | Fully free, open-source weather API. No registration needed. |
| **Ollama** | Runs locally. Install from https://ollama.ai, then `ollama pull llama3.2`. |

#### Weather APIs

| Key | Service | Free tier | Sign-up URL |
|---|---|---|---|
| `WEATHERAPI_KEY` | WeatherAPI | 1M calls/month | https://www.weatherapi.com/signup.aspx |
| `OPENWEATHERMAP_KEY` | OpenWeatherMap | 1,000 calls/day | https://home.openweathermap.org/users/sign_up |

**WeatherAPI:** Sign up → dashboard auto-shows your API key on first login.

**OpenWeatherMap:** Sign up → *API keys* tab → default key is created automatically. New keys take up to 10 minutes to activate.

#### News APIs

| Key | Service | Free tier | Sign-up URL |
|---|---|---|---|
| `THENEWSAPI_KEY` | TheNewsAPI | 100 requests/day | https://www.thenewsapi.com/register |
| `GNEWS_KEY` | GNews | 100 requests/day | https://gnews.io/register |
| `NEWSAPI_KEY` | NewsAPI | 100 requests/day | https://newsapi.org/register |

**TheNewsAPI:** Register → *API Token* shown immediately on dashboard.

**GNews:** Register → *Dashboard* → copy API key.

**NewsAPI:** Register → API key shown on the registration confirmation page. Free plan only works from `localhost`; non-localhost requests require a paid plan.

#### LLM APIs (optional — only if not using Ollama)

| Key | Service | Pricing | Console URL |
|---|---|---|---|
| `OPENAI_API_KEY` | OpenAI | Pay-per-use | https://platform.openai.com/api-keys |
| `ANTHROPIC_API_KEY` | Anthropic | Pay-per-use | https://console.anthropic.com/ |

**OpenAI:** Sign in → *API keys* → *Create new secret key* → copy immediately (shown once). Requires billing setup.

**Anthropic:** Sign in → *API Keys* → *Create Key* → copy immediately. Requires billing setup.

### 2. Start with Docker Compose (recommended)

```bash
# Start all 7 services (Ollama runs in Docker)
docker compose --profile ollama up --build

# OR: start without Ollama (using local Ollama at localhost:11434)
# Add OLLAMA_BASE_URL=http://host.docker.internal:11434 to .env first
docker compose up --build
```

Services available after startup:

| URL | Service |
|---|---|
| http://localhost:3000 | React UI |
| http://localhost:8080/api/config/providers | Backend health check |
| http://localhost:8080/h2-console | H2 database console |

```bash
# Individual service rebuild
docker compose build backend
docker compose up -d backend

# Stop
docker compose down
docker compose --profile ollama down -v   # also removes ollama-models volume
```

### 2b. Start MCP servers only (for local backend dev)

```bash
# Weather servers (Open-Meteo, WeatherAPI, OpenWeatherMap)
docker compose -f docker-compose-mcp-weather.yml up --build -d

# News server (TheNewsAPI, GNews, NewsAPI)
docker compose -f docker-compose-mcp-news.yml up --build -d

# Verify all 4 containers are running
docker ps
```

## Building

```bash
./gradlew build

# Skip tests
./gradlew build -x test

# Clean build
./gradlew clean build
```

## Running

MCP servers must be running before starting the application (see Setup above).

### Backend

```bash
# Run the application
./gradlew :orchestrator-web:bootRun

# Or run the JAR
java -jar orchestrator-web/build/libs/orchestrator-web-0.0.1-SNAPSHOT.jar
```

### Frontend

```bash
cd orchestrator-frontend

# Install dependencies (first time only)
npm install

# Start dev server — opens http://localhost:3000
# API requests are proxied to http://localhost:8080
npm start
```

## AI Providers

Active provider is controlled by `ai.provider.active` in `application.yml`.

| Provider | Default | Enable via |
|---|---|---|
| Ollama | yes | runs locally on `localhost:11434` |
| OpenAI | no | set `OPENAI_API_KEY` env var |
| Anthropic | no | set `ANTHROPIC_API_KEY` env var |

### Default models

| Provider | Model |
|---|---|
| Ollama | `llama3.2` |
| OpenAI | `gpt-4o-mini` |
| Anthropic | `claude-sonnet-4-6` |

### Switching providers

```yaml
# application.yml
ai:
  provider:
    active: openai  # ollama | openai | anthropic
```

## Database

H2 file-based database, persisted at `./data/orchestrator`.

| Property | Value |
|---|---|
| Console URL | http://localhost:8080/h2-console |
| JDBC URL | `jdbc:h2:file:./data/orchestrator` |
| Username | `sa` |
| Password | _(empty)_ |

### Schema

**conversations**

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK, auto-generated |
| timestamp | TIMESTAMP | conversation start time |
| user_id | VARCHAR | optional |
| topic | VARCHAR | optional |

**messages**

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK, auto-generated |
| conversation_id | UUID | FK to conversations |
| role | VARCHAR | USER / ASSISTANT / SYSTEM |
| content | TEXT | message body |
| timestamp | TIMESTAMP | message time |

## MCP Servers

Standalone Python MCP servers using **Streamable HTTP transport** (`mcp>=1.26.0`). Spring AI connects to each at `http://localhost:<port>/mcp`.

### Weather Servers

| Server | Port | API Key | Tools |
|---|---|---|---|
| Open-Meteo | 8101 | None | `get_current_weather`, `get_weather_forecast` |
| WeatherAPI | 8103 | `WEATHERAPI_KEY` | `get_current_weather`, `get_weather_forecast` |
| OpenWeatherMap | 8104 | `OPENWEATHERMAP_KEY` | `get_current_weather`, `get_weather_forecast` |

### News Server

| Server | Port | API Keys | Tools |
|---|---|---|---|
| News Aggregator | 8102 | `THENEWSAPI_KEY`, `GNEWS_KEY`, `NEWSAPI_KEY` | `get_news_thenewsapi`, `get_news_gnews`, `get_news_newsapi`, `get_all_news` |

`get_all_news(query, count, sources)` — `sources` is comma-separated: `thenewsapi,gnews,newsapi` or `all`.

All servers return a descriptive message when an API key is missing — no crash.

### Testing MCP Servers

```bash
# List tools — Open-Meteo (no key required)
curl -s -X POST http://localhost:8101/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Call a weather tool
curl -s -X POST http://localhost:8101/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_current_weather","arguments":{"location":"London"}}}'

# List tools — News server
curl -s -X POST http://localhost:8102/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### Stopping MCP Servers

```bash
docker compose -f docker-compose-mcp-weather.yml down
docker compose -f docker-compose-mcp-news.yml down
```

## MCP Client Configuration

Spring AI connects to MCP servers via `spring.ai.mcp.client.streamable-http.connections` in `application.yml`:

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SYNC
        request-timeout: 30s
        streamable-http:
          connections:
            weather-openmeteo:
              url: http://localhost:8101
            weather-weatherapi:
              url: http://localhost:8103
            weather-openweathermap:
              url: http://localhost:8104
            news-aggregator:
              url: http://localhost:8102
```

`WeatherMcpClient` and `NewsMcpClient` in `orchestrator-mcp` wrap the auto-configured `McpSyncClient` beans and expose `getToolCallbacks()` for use in agents.

## Frontend

React 18 single-page app (`orchestrator-frontend/`). Communicates with the backend via `proxy: http://localhost:8080` — no CORS setup needed in development.

### Layout

```
┌─────────────────┬──────────────────────────────────────┐
│  Daily Context  │  Messages (USER / ASSISTANT bubbles)  │
│  AI             │                                       │
│  [+ New Chat]   │                                       │
│                 │                                       │
│  Topic 1        ├───────────────────────────────────────┤
│  Topic 2        │  Weather: □ openmeteo □ weatherapi    │
│  …              │  News:    □ thenewsapi □ gnews        │
│                 │  ┌──────────────────────┐  [Send]     │
│  Providers: …   │  │  Ask something…      │             │
└─────────────────┴──┴──────────────────────┴─────────────┘
```

- Sidebar: conversation list (topic + date), delete button, available AI providers shown as info
- Messages: USER bubbles right (blue), ASSISTANT bubbles left (gray, `white-space: pre-wrap`)
- Filters: unchecked = use all providers/sources; check to restrict the request
- Enter sends, Shift+Enter inserts newline

### Production build

```bash
cd orchestrator-frontend && npm run build
# Output in orchestrator-frontend/build/ — served by Nginx in Docker
```

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/chat` | Process a query; returns answer + conversationId + durationMs |
| `GET` | `/api/conversations` | List all conversations (no messages) |
| `GET` | `/api/conversations/{id}` | Get conversation with all messages |
| `DELETE` | `/api/conversations/{id}` | Delete conversation and its messages |
| `GET` | `/api/config/providers` | List available AI providers |

### Chat request / response

```json
// POST /api/chat
{
  "query": "What is the weather in London?",
  "conversationId": null,
  "weatherProviders": [],
  "newsSources": []
}

// Response
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "answer": "...",
  "durationMs": 3241
}
```

- `conversationId` — omit or pass `null` to start a new conversation; pass an existing UUID to continue
- `weatherProviders` / `newsSources` — empty array or omit to use all available providers/sources

### Example curl commands

```bash
# Chat
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"What is the weather in London?","weatherProviders":[],"newsSources":[]}'

# List conversations
curl -s http://localhost:8080/api/conversations

# Get conversation with messages
curl -s http://localhost:8080/api/conversations/{id}

# Delete conversation
curl -s -X DELETE http://localhost:8080/api/conversations/{id}

# List available providers
curl -s http://localhost:8080/api/config/providers
```

## Project Status

| Phase | Title | Status |
|---|---|---|
| 1 | Project Foundation | ✅ Complete |
| 2 | Core Domain & H2 Integration | ✅ Complete |
| 3 | AI Provider Configuration | ✅ Complete |
| 4 | Weather MCP Servers | ✅ Complete |
| 5 | News MCP Server | ✅ Complete |
| 6 | MCP Client Integration | ✅ Complete |
| 7 | Agent Implementation | ✅ Complete |
| 8 | REST API | ✅ Complete |
| 9 | React Frontend | ✅ Complete |
| 10 | Docker Compose Integration | ✅ Complete |
| 11 | Testing & Documentation | ✅ Complete |
| 12 | Enhancements (Optional) | 🔲 |

**Build:** `BUILD SUCCESSFUL` — 43 tests pass, full stack verified end-to-end.

For the detailed phase-by-phase plan, fixes applied, and architectural decisions see [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md).

### Phase 10 highlights — Docker Compose

- `Dockerfile` (root) — multi-stage: Gradle build → JRE 25 runtime
- `orchestrator-frontend/Dockerfile` — multi-stage: Node build → Nginx runtime
- `docker-compose.yml` — all 7 services on a shared bridge network; Ollama as optional `--profile ollama`
- `application.yml` — MCP URLs + Ollama URL + AI provider controlled by env vars with localhost defaults

### Phase 11 highlights — Testing & Documentation

**Automated tests (43 total)**

| Test class | What it tests |
|---|---|
| `AgentCoordinationServiceTest` | Parallel execution, exception-to-failure conversion |
| `ConversationServiceTest` | processChat, list, get, delete, topic truncation |
| `OrchestratorServiceTest` | Intent routing, direct answer, agent dispatch |
| `ChatControllerTest` | POST /api/chat — valid, blank, missing body |
| `ConversationControllerTest` | GET list/single, DELETE, 404 cases |
| `ConfigControllerTest` | GET /api/config/providers |
| `ConversationRepositoryTest` | JPA queries, UUID generation, userId filtering |
| `MessageRepositoryTest` | Conversation scoping, deleteByConversationId |

**Documentation**

| File | Contents |
|---|---|
| `API_DOCUMENTATION.md` | All endpoints, request/response schemas, curl examples |
| `DEPLOYMENT.md` | Docker Compose quick start, env vars, service map, local dev |
| `TROUBLESHOOTING.md` | MCP connectivity, AI provider errors, database issues, diagnostics |

**Spring Boot 4.0 testing notes**

- `@DataJpaTest` was removed in Spring Boot 4.0 → replaced with `@ExtendWith(SpringExtension.class)` + custom `JpaTestConfig` (manual H2 + Hibernate)
- `@WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure`; requires `spring-boot-starter-webmvc-test` dependency
- A `TestApplication` class in the common parent package is needed for `@WebMvcTest` to find `@SpringBootApplication`

## License

Copyright © 2026 Daily Context AI. All rights reserved.
