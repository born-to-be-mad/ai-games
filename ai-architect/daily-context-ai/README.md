# Daily Context AI

An AI-powered agent orchestrator application that answers questions about current weather and latest news using Spring AI, MCP servers, and multiple AI providers.

## Overview

Daily Context AI uses the Orchestrator-Workers pattern to intelligently route user queries to specialized agents:
- **Weather Agent**: Queries multiple weather providers (Open-Meteo, WeatherAPI, OpenWeatherMap)
- **News Agent**: Aggregates news from multiple sources (TheNews API, GNews.io, NewsAPI)

## Features

- Multi-provider weather information (configurable)
- Multi-source news aggregation (configurable)
- AI Provider flexibility (Ollama, OpenAI, Anthropic)
- Conversation history with H2 database persistence
- React web interface
- Docker Compose deployment

## Technology Stack

- **Java**: 25
- **Spring Boot**: 4.0.3
- **Spring AI**: 1.1.2
- **Gradle**: 9.2.1
- **H2 Database**: File-based
- **React**: Frontend
- **Docker Compose**: Deployment

## Architecture

### Modules
- `orchestrator-core` - Core domain logic, JPA entities, AI providers
- `orchestrator-web` - REST API, web controllers
- `orchestrator-mcp` - MCP client integrations
- `orchestrator-frontend` - React web UI

### Ports
- **8080** - Spring Boot Application
- **8101** - Open-Meteo MCP Server
- **8103** - WeatherAPI MCP Server
- **8104** - OpenWeatherMap MCP Server
- **8102** - News Aggregator MCP Server
- **11434** - Ollama

## Dependencies

### Spring AI Model Starters (New Naming Convention)
Spring AI 1.1.2 uses updated artifact names:
- `spring-ai-starter-model-ollama`
- `spring-ai-starter-model-openai`
- `spring-ai-starter-model-anthropic`

### Key Libraries
- Spring Boot Starter Web
- Spring Boot Starter Data JPA
- Spring Boot Starter Validation
- Spring Boot Starter WebFlux (for MCP)
- H2 Database
- Jackson Databind

## Building

```bash
# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Build without tests
./gradlew build -x test
```

## Running

```bash
# Run the application
./gradlew :orchestrator-web:bootRun

# Or run the JAR after building
java -jar orchestrator-web/build/libs/orchestrator-web-0.0.1-SNAPSHOT.jar
```

## AI Providers

Active provider is controlled by `ai.provider.active` in `application.yml` or overridden per request.

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

Or pass `provider` field in the chat request to override per call (Phase 8).

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
|conversation_id | UUID | FK to conversations |
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

All servers return a descriptive error string when the API key is missing — no crash.

### Starting MCP Servers

```bash
# Copy and fill in API keys
cp .env.example .env

# Start weather servers
docker compose -f docker-compose-mcp-weather.yml up --build -d

# Start news server
docker compose -f docker-compose-mcp-news.yml up --build -d

# Check containers
docker ps

# Check logs
docker logs weather-mcp-openmeteo
docker logs news-mcp
```

### Testing MCP Servers

```bash
# List tools (Open-Meteo — no key required)
curl -s -X POST http://localhost:8101/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# Call a tool
curl -s -X POST http://localhost:8101/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_current_weather","arguments":{"location":"London"}}}'

# Test news server (requires at least one key in .env)
curl -s -X POST http://localhost:8102/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### Stopping MCP Servers

```bash
docker compose -f docker-compose-mcp-weather.yml down
docker compose -f docker-compose-mcp-news.yml down
```

## Project Status

**Phase 1: Project Foundation** ✅ Complete
- Multi-module Gradle structure with Java 25
- Spring Boot 4.0.3 + Spring AI 1.1.2 configured
- Package structure: `ai.architect.orchestrator`
- AI Providers configured: Ollama (default), OpenAI, Anthropic
- Build verified and working

**Phase 2: Core Domain & H2 Integration** ✅ Complete
- `Conversation` and `Message` JPA entities created
- `MessageRole` enum: USER / ASSISTANT / SYSTEM
- `ConversationRepository` and `MessageRepository` with custom query methods
- H2 file-based database configured (`./data/orchestrator`)
- H2 console enabled at `/h2-console`
- Build verified and working

**Phase 3: AI Provider Configuration** ✅ Complete
- `AiProviderProperties` — `@ConfigurationProperties("ai.provider")`, active provider switchable via config
- `OllamaConfig` / `OpenAiConfig` / `AnthropicConfig` — conditional `@Configuration` per provider
- `ChatClientFactory` — injects `List<ChatModel>`, resolves provider by class name at startup
- `ProviderConfigService` — `getChatClient()` (active provider), `getChatClient(String)` (per-request override), `getAvailableProviders()`
- Ollama enabled by default (`llama3.2` @ `localhost:11434`)
- OpenAI / Anthropic enabled via `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` env vars
- Build verified and working

**Phase 4: Weather MCP Servers** ✅ Complete
- `docker/weather-mcp-openmeteo` — Open-Meteo, no key, ports 8101; geocoding → current + forecast
- `docker/weather-mcp-weatherapi` — WeatherAPI, `WEATHERAPI_KEY`, port 8103
- `docker/weather-mcp-openweathermap` — OpenWeatherMap, `OPENWEATHERMAP_KEY`, port 8104; geocodes via `/geo/1.0/direct`
- All use Streamable HTTP transport (`mcp>=1.8.0`), Python 3.12-slim Docker images
- `docker-compose-mcp-weather.yml` — builds and starts all 3 weather servers on `daily-context-network`

**Phase 5: News MCP Server** ✅ Complete
- `docker/news-mcp` — aggregates TheNewsAPI, GNews, NewsAPI; port 8102
- Tools: `get_news_thenewsapi`, `get_news_gnews`, `get_news_newsapi`, `get_all_news`
- `get_all_news` dispatches to selected sources via comma-separated `sources` param
- `docker-compose-mcp-news.yml` — builds and starts news server on `daily-context-network`
- `.env.example` — template for all 7 API keys

**Phase 6: MCP Client Integration** ✅ Complete
- `spring-ai-starter-mcp-client` added to `orchestrator-mcp/build.gradle`
- `orchestrator-mcp` added as dependency to `orchestrator-web`
- `application.yml` — 4 MCP connections via `spring.ai.mcp.client.streamable-http.connections` (SYNC, 30s timeout)
- `WeatherMcpClientConfig.java` — `@Bean SyncMcpToolCallbackProvider weatherToolCallbackProvider()` (filters 3 weather clients by server name)
- `NewsMcpClientConfig.java` — `@Bean SyncMcpToolCallbackProvider newsToolCallbackProvider()` (filters news-aggregator client)
- `WeatherMcpClient.java` — `@Component`; `getToolCallbacks()` / `getToolCallbacks(Set<String> providers)` / `getConnectedProviders()`
- `NewsMcpClient.java` — `@Component`; `getToolCallbacks()` / `isConnected()`
- Build verified: `BUILD SUCCESSFUL`

**Next Phase:** Phase 7 — Agent Implementation (Orchestrator-Workers)

For detailed implementation plan, see [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)

**Upcoming Phases:**
- Phase 7: Agent Implementation (Orchestrator-Workers)
- Phase 8: REST API Implementation
- Phase 9: React Frontend
- Phase 10: Docker Compose Integration
- Phase 11: Testing & Documentation
- Phase 12: Enhancements (Optional)

## License

Copyright © 2026 Daily Context AI. All rights reserved.
