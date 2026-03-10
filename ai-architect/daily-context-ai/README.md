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

**Next Phase:** Phase 4 & 5 - MCP Server Setup (Weather + News)

For detailed implementation plan, see [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)

**Upcoming Phases:**
- Phase 4-5: MCP Server Setup (Weather + News)
- Phase 6: MCP Client Integration
- Phase 7: Agent Implementation (Orchestrator-Workers)
- Phase 8: REST API Implementation
- Phase 9: React Frontend
- Phase 10: Docker Compose Integration
- Phase 11: Testing & Documentation
- Phase 12: Enhancements (Optional)

## License

Copyright © 2026 Daily Context AI. All rights reserved.
