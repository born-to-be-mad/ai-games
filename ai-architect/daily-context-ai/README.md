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
| AI | Spring AI 1.1.2 |
| Build | Gradle 9.2.1 + `java-library` |
| Database | H2 (file-based) |
| MCP Servers | Python 3.12, `mcp>=1.8.0` |
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

Spring AI 1.1.2 uses updated artifact naming (`spring-ai-starter-*`):

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

### 2. Start MCP servers

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

```bash
# Run the application
./gradlew :orchestrator-web:bootRun

# Or run the JAR
java -jar orchestrator-web/build/libs/orchestrator-web-0.0.1-SNAPSHOT.jar
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

Standalone Python MCP servers using **Streamable HTTP transport** (`mcp>=1.8.0`). Spring AI connects to each at `http://localhost:<port>/mcp`.

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

## Project Status

**Phase 1: Project Foundation** ✅ Complete
- Multi-module Gradle structure with Java 25
- Spring Boot 4.0.3 + Spring AI 1.1.2 configured
- Package structure: `ai.architect.orchestrator`
- AI providers configured: Ollama (default), OpenAI, Anthropic
- Build verified: `BUILD SUCCESSFUL`

**Phase 2: Core Domain & H2 Integration** ✅ Complete
- `Conversation` and `Message` JPA entities (UUID PKs)
- `MessageRole` enum: USER / ASSISTANT / SYSTEM
- `ConversationRepository` / `MessageRepository` with custom query methods
- H2 file-based database at `./data/orchestrator`, console at `/h2-console`
- Build verified: `BUILD SUCCESSFUL`

**Phase 3: AI Provider Configuration** ✅ Complete
- `AiProviderProperties` — `@ConfigurationProperties("ai.provider")`, runtime provider switching
- `OllamaConfig` / `OpenAiConfig` / `AnthropicConfig` — conditional `@Configuration` per provider
- `ChatClientFactory` — resolves active `ChatModel` by class name at startup
- `ProviderConfigService` — `getChatClient()`, `getChatClient(String)`, `getAvailableProviders()`
- Build verified: `BUILD SUCCESSFUL`

**Phase 4: Weather MCP Servers** ✅ Complete
- `docker/weather-mcp-openmeteo` — Open-Meteo, no key, port 8101; geocoding → current weather + forecast
- `docker/weather-mcp-weatherapi` — WeatherAPI, `WEATHERAPI_KEY`, port 8103
- `docker/weather-mcp-openweathermap` — OpenWeatherMap, `OPENWEATHERMAP_KEY`, port 8104; geocodes via `/geo/1.0/direct`
- All: Python 3.12-slim, Streamable HTTP transport, `mcp>=1.8.0`, `httpx>=0.27.0`
- `docker-compose-mcp-weather.yml` — builds and starts all 3 servers on `daily-context-network`

**Phase 5: News MCP Server** ✅ Complete
- `docker/news-mcp` — aggregates TheNewsAPI, GNews, NewsAPI; port 8102
- Tools: `get_news_thenewsapi`, `get_news_gnews`, `get_news_newsapi`, `get_all_news`
- `get_all_news` dispatches to selected sources via comma-separated `sources` param
- `docker-compose-mcp-news.yml` — builds and starts news server on `daily-context-network`
- `.env.example` — template for all 7 API keys

**Phase 6: MCP Client Integration** ✅ Complete
- `orchestrator-core` and `orchestrator-mcp` promoted to `java-library`; Spring AI starters and MCP client exposed as `api` so types (`ChatClient`, `BeanOutputConverter`, `ToolCallback`) are visible to `orchestrator-web`
- Lombok added to root `subprojects` block (`compileOnly` + `annotationProcessor`, version managed by Spring Boot BOM)
- `application.yml` — 4 Streamable HTTP connections (SYNC mode, 30s timeout)
- `WeatherMcpClientConfig` / `NewsMcpClientConfig` — `@Configuration @RequiredArgsConstructor`; `SyncMcpToolCallbackProvider.builder()`
- `WeatherMcpClient` / `NewsMcpClient` — `@Component @Slf4j`; `getToolCallbacks()`, `isConnected()`
- Refactors: `AiProviderProperties` → record (no `@Component`); entities → Lombok; services → `@RequiredArgsConstructor`
- Build verified: `BUILD SUCCESSFUL`

**Phase 7: Agent Implementation** ✅ Complete
- `spring.threads.virtual.enabled: true` — virtual threads for Tomcat + Spring async executor
- `VirtualThreadConfig` — `@Bean Executor virtualThreadExecutor()` = `Executors.newVirtualThreadPerTaskExecutor()`
- `QueryIntent` record — `needsWeather`, `needsNews`, `location`, `newsQuery`
- `AgentResult` record — `agentName`, `content`, `success`, `errorMessage`; `success()` / `failure()` factory methods
- `AgentCoordinationService` — `runParallel()` submits tasks via `CompletableFuture.supplyAsync` on virtual thread executor
- `WeatherAgent` / `NewsAgent` — `@Component @Slf4j @RequiredArgsConstructor`; system prompt injected via `AgentProperties`
- `OrchestratorService` — `BeanOutputConverter<QueryIntent>` intent analysis → parallel agent dispatch → LLM synthesis
- `AgentProperties` record — `@ConfigurationProperties("agent")`; nested `Weather` / `News` records with `systemPrompt`; prompts externalised to `application.yml`
- `AiProviderProperties` — `@Component` removed; registered via `@ConfigurationPropertiesScan` on `Application`
- `ProviderConfigService` — single `@Getter ChatClient chatClient` field; built eagerly at startup from the active provider
- Build verified: `BUILD SUCCESSFUL`

**Next Phase:** Phase 8 — REST API

For detailed implementation plan, see [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)

**Upcoming Phases:**
- Phase 8: REST API Implementation
- Phase 9: React Frontend
- Phase 10: Docker Compose Integration
- Phase 11: Testing & Documentation
- Phase 12: Enhancements (Optional)

## License

Copyright © 2026 Daily Context AI. All rights reserved.
