# Daily Context AI — Implementation Plan

## Project Overview

**Daily Context AI** is an AI-powered agent orchestrator that answers questions about current weather and latest news using Spring AI, MCP servers, and multiple AI providers.

### Technology Stack

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

### Architecture — Orchestrator-Workers Pattern

```
User Query
    │
    ▼
OrchestratorService          ← intent analysis via LLM (BeanOutputConverter → QueryIntent)
    │
    ├─── CompletableFuture (virtual thread) ──► WeatherAgent  ──► Weather MCP tools
    │                                                               (openmeteo / weatherapi / owm)
    └─── CompletableFuture (virtual thread) ──► NewsAgent     ──► News MCP tools
                                                                    (thenewsapi / gnews / newsapi)
    │
    ▼
AgentCoordinationService     ← collects List<AgentResult>
    │
    ▼
OrchestratorService          ← synthesize via LLM → final response
```

### Ports

| Port | Service |
|---|---|
| 8080 | Spring Boot Application |
| 8101 | Open-Meteo MCP Server |
| 8102 | News Aggregator MCP Server |
| 8103 | WeatherAPI MCP Server |
| 8104 | OpenWeatherMap MCP Server |
| 11434 | Ollama |

---

## Phase Status

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
| 12 | Enhancements (Optional) | ✅ Complete |

---

## ✅ Phase 1: Project Foundation

**Goal:** Multi-module Gradle project skeleton with all tooling configured.

**Deliverables:**
- Root `build.gradle` — Spring Boot 4.0.3, Spring AI 2.0.0-M2 BOM, Java 25 toolchain, Lombok (managed by BOM)
- `settings.gradle` — all module definitions
- Module structure:
  - `orchestrator-core` — domain, JPA, AI providers (`java-library`, `api` scope for Spring AI)
  - `orchestrator-web` — bootable app, REST API, agents, services
  - `orchestrator-mcp` — MCP client integrations (`java-library`, `api` scope for MCP client)
  - `orchestrator-frontend` — React UI
- `Application.java` with `@SpringBootApplication(scanBasePackages = "ai.architect.orchestrator")`
- `application.yml` — base config
- `.gitignore`
- Build verified: `BUILD SUCCESSFUL`

---

## ✅ Phase 2: Core Domain & H2 Integration

**Goal:** JPA entities, repositories, and H2 database configuration.

**Files created:**
```
orchestrator-core/src/main/java/ai/architect/orchestrator/
├── domain/
│   ├── Conversation.java    @Entity — id (UUID), timestamp (@Builder.Default), userId, topic
│   ├── Message.java         @Entity — id, conversationId (FK), role, content (TEXT), timestamp
│   └── MessageRole.java     enum: USER, ASSISTANT, SYSTEM
└── repository/
    ├── ConversationRepository.java  findByUserIdOrderByTimestampDesc, findAllByOrderByTimestampDesc
    └── MessageRepository.java       findByConversationIdOrderByTimestamp, deleteByConversationId
```

**Key decisions:**
- Entities use Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`
- `timestamp` fields use `@Builder.Default` with `LocalDateTime.now()`
- H2 file-based at `./data/orchestrator`; console at `/h2-console`

---

## ✅ Phase 3: AI Provider Configuration

**Goal:** Multi-provider AI support with runtime switching.

**Files created:**
```
orchestrator-core/src/main/java/ai/architect/orchestrator/
├── config/
│   ├── AiProviderProperties.java   record @ConfigurationProperties("ai.provider")
│   ├── OllamaConfig.java           @ConditionalOnProperty(spring.ai.ollama.base-url)
│   ├── OpenAiConfig.java           @ConditionalOnProperty(spring.ai.openai.api-key)
│   └── AnthropicConfig.java        @ConditionalOnProperty(spring.ai.anthropic.api-key)
└── service/
    ├── ChatClientFactory.java      injects List<ChatModel>, resolves by class name
    └── ProviderConfigService.java  getChatClient(), getChatClient(String), getAvailableProviders()
```

**Providers:**

| Provider | Default | Model | Enable via |
|---|---|---|---|
| Ollama | yes | `llama3.2` | runs locally at `localhost:11434` |
| OpenAI | no | `gpt-4o-mini` | `OPENAI_API_KEY` env var |
| Anthropic | no | `claude-sonnet-4-6` | `ANTHROPIC_API_KEY` env var |

---

## ✅ Phase 4: Weather MCP Servers

**Goal:** Three standalone Python MCP servers for weather data.

**Files created:**
```
daily-context-ai/
├── docker/
│   ├── weather-mcp-openmeteo/     Dockerfile, server.py, requirements.txt
│   ├── weather-mcp-weatherapi/    Dockerfile, server.py, requirements.txt
│   └── weather-mcp-openweathermap/ Dockerfile, server.py, requirements.txt
└── docker-compose-mcp-weather.yml
```

**Servers:**

| Server | Port | Key | Tools |
|---|---|---|---|
| Open-Meteo | 8101 | None | `get_current_weather`, `get_weather_forecast` |
| WeatherAPI | 8103 | `WEATHERAPI_KEY` | `get_current_weather`, `get_weather_forecast` |
| OpenWeatherMap | 8104 | `OPENWEATHERMAP_KEY` | `get_current_weather`, `get_weather_forecast` |

**Common pattern:** Python 3.12-slim, `mcp>=1.26.0`, `httpx>=0.27.0`, Streamable HTTP transport,
`FastMCP("name", host="0.0.0.0", port=8080)` + `mcp.run(transport="streamable-http")`. Missing key → descriptive string, no crash.

---

## ✅ Phase 5: News MCP Server

**Goal:** Aggregator MCP server for three news APIs.

**Files created:**
```
daily-context-ai/
├── docker/
│   └── news-mcp/           Dockerfile, server.py, requirements.txt
├── docker-compose-mcp-news.yml
└── .env.example            all 7 API keys
```

**Tools exposed:**

| Tool | Source | Key |
|---|---|---|
| `get_news_thenewsapi(query, count)` | api.thenewsapi.com | `THENEWSAPI_KEY` |
| `get_news_gnews(query, count)` | gnews.io | `GNEWS_KEY` |
| `get_news_newsapi(query, count)` | newsapi.org | `NEWSAPI_KEY` |
| `get_all_news(query, count, sources)` | all of the above | — |

`sources` param: comma-separated `thenewsapi,gnews,newsapi` or `all`.

---

## ✅ Phase 6: MCP Client Integration

**Goal:** Spring AI connects to all 4 MCP servers via Streamable HTTP.

**Build changes:**
- `orchestrator-core/build.gradle` — added `java-library` plugin; Spring AI starters promoted to `api` so types (`ChatClient`, `BeanOutputConverter`, `ToolCallback`) are visible to consumers
- `orchestrator-mcp/build.gradle` — added `java-library` plugin; `spring-ai-starter-mcp-client` and `orchestrator-core` promoted to `api`
- `orchestrator-web/build.gradle` — added `implementation project(':orchestrator-mcp')`
- Root `build.gradle` — Lombok added to `subprojects` block (`compileOnly` + `annotationProcessor`)

**application.yml additions:**
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
            weather-openmeteo:   { url: http://localhost:8101 }
            weather-weatherapi:  { url: http://localhost:8103 }
            weather-openweathermap: { url: http://localhost:8104 }
            news-aggregator:     { url: http://localhost:8102 }
```

**Files created:**
```
orchestrator-mcp/src/main/java/ai/architect/orchestrator/mcp/
├── config/
│   ├── WeatherMcpClientConfig.java  @Configuration @RequiredArgsConstructor
│   │                                @Bean weatherToolCallbackProvider() — SyncMcpToolCallbackProvider.builder()
│   └── NewsMcpClientConfig.java     @Configuration @RequiredArgsConstructor
│                                    @Bean newsToolCallbackProvider() — SyncMcpToolCallbackProvider.builder()
└── client/
    ├── WeatherMcpClient.java        @Component @Slf4j
    │                                getToolCallbacks(), getToolCallbacks(Set<String>), getConnectedProviders()
    └── NewsMcpClient.java           @Component @Slf4j
                                     getToolCallbacks(), isConnected()
```

**Refactors:**
- `AiProviderProperties` → Java `record` (no `@Component`); registered via `@ConfigurationPropertiesScan`
- `Conversation` / `Message` entities → Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, `@Builder.Default` on `timestamp`
- `ProviderConfigService` → single `@Getter ChatClient chatClient` field built eagerly at startup; no factory stored
- `WeatherMcpClient` / `NewsMcpClient` → `@Slf4j`
- `WeatherMcpClientConfig` / `NewsMcpClientConfig` → `@RequiredArgsConstructor`

---

## ✅ Phase 7: Agent Implementation

**Goal:** Orchestrator-Workers pattern with virtual-thread parallelism.

**Virtual threads enabled:**
```yaml
spring:
  threads:
    virtual:
      enabled: true   # Tomcat + Spring async executor use virtual threads
```

**Files created:**
```
orchestrator-core/src/main/java/ai/architect/orchestrator/
├── agent/
│   ├── QueryIntent.java       record: needsWeather, needsNews, location, newsQuery
│   └── AgentResult.java       record: agentName, content, success, errorMessage
│                              + factory methods success() / failure()
└── config/
    ├── AgentProperties.java   @ConfigurationProperties("agent")
    │                          nested records: Weather(systemPrompt), News(systemPrompt)
    └── VirtualThreadConfig.java  @Bean Executor virtualThreadExecutor()
                                  = Executors.newVirtualThreadPerTaskExecutor()

orchestrator-web/src/main/java/ai/architect/orchestrator/
├── agent/
│   ├── WeatherAgent.java    @Component @Slf4j @RequiredArgsConstructor
│   │                        execute(query, providers, aiProvider) → AgentResult
│   │                        system prompt from AgentProperties.weather().systemPrompt()
│   └── NewsAgent.java       @Component @Slf4j @RequiredArgsConstructor
│                            execute(query, sources, aiProvider) → AgentResult
│                            system prompt from AgentProperties.news().systemPrompt()
└── service/
    ├── AgentCoordinationService.java  @Service @Slf4j @RequiredArgsConstructor
    │                                  runParallel(List<Supplier<AgentResult>>) → List<AgentResult>
    │                                  CompletableFuture.supplyAsync on virtualThreadExecutor
    └── OrchestratorService.java       @Service @Slf4j @RequiredArgsConstructor
                                       process(query, weatherProviders, newsSources, aiProvider)
                                       1. analyzeIntent() — BeanOutputConverter<QueryIntent>
                                       2. coordinationService.runParallel(tasks)
                                       3. synthesize() — final LLM call
```

**application.yml additions:**
```yaml
spring:
  threads:
    virtual:
      enabled: true

agent:
  weather:
    system-prompt: "You are a weather assistant..."   # overridable per environment
  news:
    system-prompt: "You are a news assistant..."      # overridable per environment
```

**Key decisions:**
- `@ConfigurationPropertiesScan("ai.architect.orchestrator")` on `Application` registers all `@ConfigurationProperties` records — no `@Component` or `@EnableConfigurationProperties` needed
- `ProviderConfigService` holds a single `@Getter ChatClient chatClient` field built eagerly at startup; per-request provider switching deferred to Phase 8
- System prompts externalised to `application.yml` via `AgentProperties` — changeable without recompiling

**Execution flow:**
1. `OrchestratorService.process()` calls LLM with structured output to produce `QueryIntent`
2. Builds `List<Supplier<AgentResult>>` for needed agents (weather / news / both)
3. `AgentCoordinationService.runParallel()` submits each via `CompletableFuture.supplyAsync` on virtual thread executor
4. Each agent resolves its system prompt from `AgentProperties`, calls MCP tools through `ChatClient`
5. Results collected, LLM synthesizes a final markdown response
6. Fall-through: if intent is neither weather nor news, query answered directly by LLM

---

## ✅ Phase 8: REST API

**Goal:** REST controllers for all frontend interactions, conversation persistence, CORS config.

**Endpoints:**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/chat` | Process query, persist USER+ASSISTANT messages, return answer |
| `GET` | `/api/conversations` | List all conversations (no messages) |
| `GET` | `/api/conversations/{id}` | Get conversation with all messages |
| `DELETE` | `/api/conversations/{id}` | Delete conversation + its messages |
| `GET` | `/api/config/providers` | List available AI providers |

**Files created:**
```
orchestrator-web/src/main/java/ai/architect/orchestrator/
├── controller/
│   ├── ChatController.java         POST /api/chat — @Valid @RequestBody ChatRequest
│   ├── ConversationController.java GET /api/conversations[/{id}], DELETE /api/conversations/{id}
│   └── ConfigController.java       GET /api/config/providers
├── dto/
│   ├── ChatRequest.java            record: @NotBlank query, UUID conversationId, Set<String> weatherProviders, newsSources
│   ├── ChatResponse.java           record: UUID conversationId, String answer, long durationMs
│   ├── ConversationDTO.java        record: id, timestamp, topic, List<MessageDTO> messages
│   └── MessageDTO.java             record: id, role, content, timestamp
├── service/
│   └── ConversationService.java    resolves/creates Conversation, persists USER+ASSISTANT messages,
│                                   calls OrchestratorService, sets topic on first message
└── config/
    └── CorsConfig.java             allowedOrigins("*") on /api/**
```

**Build changes:**
- `orchestrator-web/build.gradle` — added `spring-boot-starter-data-jpa` (for `@Transactional` + JPA compilation) and `spring-boot-starter-validation`

**Key decisions:**
- `ConversationService.processChat()` — resolves existing conversation by `conversationId` or creates new; topic set to first 50 chars of query
- 404 responses use `ResponseStatusException(HttpStatus.NOT_FOUND, ...)` — no custom `@ControllerAdvice` needed
- Logging added to `ConversationService` (`@Slf4j`), `AgentCoordinationService`, and `OrchestratorService`
- Build verified: `BUILD SUCCESSFUL`

---

## ✅ Phase 9: React Frontend

**Goal:** Functional single-page chat UI connecting to the Phase 8 REST API.

**Files created:**
```
orchestrator-frontend/
├── public/
│   └── index.html                  minimal CRA shell
└── src/
    ├── index.js                    ReactDOM.createRoot entry point
    ├── App.js                      central state machine + layout
    ├── App.css                     full layout, message bubble, sidebar styles
    ├── components/
    │   ├── ChatInterface.js        messages list + input form (textarea, checkboxes, Send)
    │   ├── ResponseDisplay.js      single message bubble (USER/ASSISTANT styled)
    │   └── ConversationHistory.js  sidebar: conversation list + delete + New Chat
    └── services/
        └── ApiService.js           axios wrappers for all 5 REST endpoints
```

**Layout:**
- CSS grid: 280px sidebar + 1fr main, full 100vh
- Sidebar (dark #1e1e2e): conversation list, New Chat button, AI providers in footer
- Main: scrollable messages area + fixed input strip at bottom

**Interaction:**
- New Chat clears state; selecting a conversation loads messages via `GET /api/conversations/{id}`
- Send: optimistic user-message append → `POST /api/chat` → append assistant response → refresh sidebar
- Enter sends, Shift+Enter newlines; Send button disabled while loading
- Error banner shown on failed requests (backend down, 4xx/5xx)

**Filters (hardcoded options, empty = use all):**
- Weather: `openmeteo`, `weatherapi`, `openweathermap`
- News: `thenewsapi`, `gnews`, `newsapi`

**Key decisions:**
- Plain JS (no TypeScript) — consistent with existing package.json, no extra setup
- No UI library — pure CSS, Catppuccin-inspired dark sidebar palette
- `white-space: pre-wrap` on assistant messages — markdown readable as-is without a parser
- AI provider selector omitted — Phase 8 `ChatRequest` has no `aiProvider` field; available providers shown as info in sidebar footer
- `proxy: http://localhost:8080` in package.json handles CORS for dev server
- Build verified: `Compiled successfully`

---

## ✅ Phase 10: Docker Compose Integration

**Files to create:**
- `Dockerfile` — Spring Boot multi-stage (Java 25)
- `orchestrator-frontend/Dockerfile` — Node build + Nginx runtime
- `docker-compose.yml` — all 7 services on `daily-context-network`
  - Volumes: `./data:/app/data` (H2), `ollama-models:/root/.ollama`

---

## ✅ Phase 11: Testing & Documentation

**Full-stack smoke test** ✅
- [x] `docker compose up --build` — all 6 custom-image services start cleanly
- [x] `http://localhost:3000` — React UI loads (Nginx serving built React app)
- [x] `curl http://localhost:8080/api/config/providers` → `["openai","anthropic","ollama"]`
- [x] End-to-end chat query returns AI response (9.6 s)
- [x] All 4 MCP servers healthy (openmeteo, weatherapi, openweathermap, news-aggregator)

**Fixes applied during smoke test**
- `docker-compose.yml`: `start-period` → `start_period` (YAML schema change in Docker Compose v2)
- `orchestrator-frontend/Dockerfile`: `npm ci` → `npm install` (lock file generated on different Node version)
- All 4 MCP `server.py`: moved `host`/`port` from `mcp.run()` to `FastMCP()` constructor (`mcp` 1.26.0 API)
- `build.gradle`: Spring AI `1.1.2` → `2.0.0-M2` (`AnthropicChatAutoConfiguration` references `RestClientAutoConfiguration` removed in Spring Boot 4.0)
- `Application.java`: added `@EnableJpaRepositories(basePackages = "ai.architect.orchestrator")` (Spring Boot 4.0 modular JPA does not auto-scan sibling modules)
- `Application.java`: added `@EntityScan(basePackages = "ai.architect.orchestrator")` — import is now `org.springframework.boot.persistence.autoconfigure.EntityScan` (moved in Spring Boot 4.0)

**Automated tests** ✅ — 43 tests, all passing
- [x] `AgentCoordinationServiceTest` — parallel execution, exception-to-failure conversion (5 tests)
- [x] `ConversationServiceTest` — processChat, listConversations, getConversation, deleteConversation (10 tests)
- [x] `OrchestratorServiceTest` — weather-only, news-only, direct answer, intent parse failure, both agents (5 tests)
- [x] `ChatControllerTest` — POST /api/chat: valid, blank query, missing body (3 tests)
- [x] `ConversationControllerTest` — GET list, GET single, GET not-found, DELETE, DELETE not-found (6 tests)
- [x] `ConfigControllerTest` — GET providers: with values, empty (2 tests)
- [x] `ConversationRepositoryTest` — save, findAll, findByUserId, existsById, deleteById (6 tests)
- [x] `MessageRepositoryTest` — save, findByConversationId, deleteByConversationId, role/content (6 tests)

**Spring Boot 4.0 testing notes:**
- `@DataJpaTest` removed in Spring Boot 4.0 → replaced with `@ExtendWith(SpringExtension.class)` + custom `JpaTestConfig` (manual H2 + Hibernate setup)
- `@WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`; requires `spring-boot-starter-webmvc-test` dependency + `TestApplication` in parent package
- `@MockitoBean` from Spring Framework 6.2+ at `org.springframework.test.context.bean.override.mockito.MockitoBean`

**Documentation** ✅
- [x] `API_DOCUMENTATION.md` — all endpoints with request/response schemas, curl examples, error table
- [x] `DEPLOYMENT.md` — Docker Compose quick start, service map, environment variables, local dev, provider switching
- [x] `TROUBLESHOOTING.md` — MCP connectivity, AI provider errors, database issues, diagnostic commands

---

## ✅ Phase 12: Enhancements

**Goal:** Production-grade additions: resilience, caching, observability, rate limiting, export, user preferences, CI.

**Deliverables:**

| Item | Implementation |
|---|---|
| Declarative Resilience (SB4) | `@Retryable(maxRetries=3, delay=500, multiplier=1.5, maxDelay=5000)` + `@ConcurrencyLimit(limit=5)` on `WeatherAgent.execute()` + `NewsAgent.execute()`. `@EnableResilientMethods` in `CacheConfig`. Proxy order: `@Cacheable` → `@Retryable` → `@ConcurrencyLimit`. Exceptions propagate to `AgentCoordinationService.exceptionally()`. `ResilienceConfig.java` deleted; Resilience4j JARs removed. |
| Caching | Caffeine via `spring-boot-starter-cache` + `@EnableCaching`. `CacheConfig.java` — `weather` + `news` caches, 10-min TTL, 100 entries max. `@Cacheable` on `WeatherAgent.execute()` + `NewsAgent.execute()` with key = `query + providers + activeProvider`. Failures not cached (`unless = "!#result.success()"`). |
| Actuator + Prometheus | `spring-boot-starter-actuator` + `micrometer-registry-prometheus`. `MetricsService.java` tracks `chat.requests.total`, `agent.weather.calls.total`, `agent.news.calls.total`. Endpoints: `health`, `info`, `prometheus`, `metrics`. |
| Rate Limiting | Bucket4j core (`com.bucket4j:bucket4j-core:8.10.1`) — NOT starter (incompatible with SB4). `RateLimitFilter extends OncePerRequestFilter` with per-IP `ConcurrentHashMap<String, Bucket>`. Configurable via `rate-limit.requests-per-minute` (default: 10). Reads `X-Forwarded-For` header for Docker/Nginx proxy. Returns 429 on limit exceeded. |
| Conversation Export | OpenPDF (`com.github.librepdf:openpdf:2.0.3`). `ConversationExportService` produces JSON (Jackson) + PDF (title page with metadata + flat message list). Endpoints: `GET /api/conversations/{id}/export/json` + `/export/pdf` returning file attachments. |
| User Preferences | `UserPreferences` JPA entity + `UserPreferencesRepository` in orchestrator-core. `UserPreferencesService` + `UserPreferencesController` in orchestrator-web. `GET /api/preferences/{clientId}` auto-creates defaults. `PUT` upserts, `DELETE` resets. Frontend: `clientId` from `localStorage`, preferences loaded on startup, saved with 500ms debounce. |
| Cache Eviction | `CacheController` at `DELETE /api/cache/{cacheName}` — evicts all entries in `weather` or `news` cache. |
| GitHub Actions CI | `.github/workflows/ci.yml` at repo root. Triggers on push/PR to `main`. Two parallel jobs: `backend` (Java 25, Temurin, `./gradlew test --no-daemon`) and `frontend` (Node 20, `npm ci && npm run build`). Backend test reports uploaded as artifact on failure. |

**New files:**
```
ai-games/
└── .github/workflows/ci.yml            ← NEW: GitHub Actions CI

orchestrator-core/
└── domain/UserPreferences.java
└── repository/UserPreferencesRepository.java

orchestrator-web/
├── config/CacheConfig.java             ← @EnableResilientMethods added
├── filter/RateLimitFilter.java
├── service/MetricsService.java
├── service/ConversationExportService.java
├── service/UserPreferencesService.java
├── controller/UserPreferencesController.java
├── controller/CacheController.java
└── dto/UserPreferencesDTO.java
```

**Deleted files:**
- `orchestrator-web/config/ResilienceConfig.java` — replaced by SB4 declarative annotations

**Modified files:**
- `orchestrator-web/build.gradle` — removed Resilience4j JARs, added `spring-boot-starter-aspectj`
- `orchestrator-web/src/main/resources/application.yml` — `management` + `rate-limit` sections
- `orchestrator-core/src/main/java/.../service/ProviderConfigService.java` — added `activeProvider` field
- `orchestrator-web/src/main/java/.../agent/WeatherAgent.java` — `@Retryable` + `@ConcurrencyLimit` + `@Cacheable` + metrics; removed programmatic Resilience4j try-catch
- `orchestrator-web/src/main/java/.../agent/NewsAgent.java` — same as WeatherAgent
- `orchestrator-web/src/main/java/.../service/ConversationService.java` — metrics increment
- `orchestrator-web/src/main/java/.../controller/ConversationController.java` — export endpoints
- `orchestrator-frontend/src/App.js` — `clientId` from localStorage, preferences load/save with debounce
- `orchestrator-frontend/src/components/ConversationHistory.js` — export dropdown
- `orchestrator-frontend/src/services/ApiService.js` — preferences + export URL helpers

**Test results:** 43 tests (all pass)

**Spring Boot 4.0 compatibility notes:**
- SB4 ships `org.springframework.resilience.annotation.{Retryable,ConcurrencyLimit,EnableResilientMethods}` — no Resilience4j needed
- AOP starter renamed: `spring-boot-starter-aspectj` (was `spring-boot-starter-aop` in SB3)
- Use `com.bucket4j:bucket4j-core:8.10.1` (core JAR only — starter incompatible with SB4)

---

## Project Structure (current)

```
daily-context-ai/
├── build.gradle                      root — Lombok in subprojects block
├── settings.gradle
├── .env.example
├── docker-compose-mcp-weather.yml
├── docker-compose-mcp-news.yml
├── docker/
│   ├── weather-mcp-openmeteo/        Dockerfile, server.py, requirements.txt
│   ├── weather-mcp-weatherapi/       Dockerfile, server.py, requirements.txt
│   ├── weather-mcp-openweathermap/   Dockerfile, server.py, requirements.txt
│   └── news-mcp/                     Dockerfile, server.py, requirements.txt
│
├── orchestrator-core/               java-library; Spring AI starters as api
│   └── src/main/java/ai/architect/orchestrator/
│       ├── agent/
│       │   ├── AgentResult.java     record
│       │   └── QueryIntent.java     record
│       ├── config/
│       │   ├── AiProviderProperties.java  record + @ConfigurationProperties
│       │   ├── AnthropicConfig.java
│       │   ├── OllamaConfig.java
│       │   ├── OpenAiConfig.java
│       │   └── VirtualThreadConfig.java   @Bean virtualThreadExecutor()
│       ├── domain/
│       │   ├── Conversation.java    @Entity + Lombok
│       │   ├── Message.java         @Entity + Lombok
│       │   ├── MessageRole.java     enum
│       │   └── UserPreferences.java @Entity — clientId (unique), defaultWeatherProviders, defaultNewsSources, theme
│       ├── repository/
│       │   ├── ConversationRepository.java
│       │   ├── MessageRepository.java
│       │   └── UserPreferencesRepository.java   findByClientId, deleteByClientId
│       └── service/
│           ├── ChatClientFactory.java
│           └── ProviderConfigService.java       + activeProvider field (Lombok @Getter)
│
├── orchestrator-mcp/                java-library; orchestrator-core + mcp-client as api
│   └── src/main/java/ai/architect/orchestrator/mcp/
│       ├── client/
│       │   ├── NewsMcpClient.java
│       │   └── WeatherMcpClient.java
│       └── config/
│           ├── NewsMcpClientConfig.java
│           └── WeatherMcpClientConfig.java
│
├── orchestrator-web/                bootable app
│   └── src/main/java/ai/architect/orchestrator/
│       ├── agent/
│       │   ├── NewsAgent.java       @Retryable + @ConcurrencyLimit + @Cacheable + MetricsService
│       │   └── WeatherAgent.java    @Retryable + @ConcurrencyLimit + @Cacheable + MetricsService
│       ├── config/
│       │   ├── CacheConfig.java     @EnableCaching + @EnableResilientMethods — Caffeine, 10-min TTL
│       │   └── CorsConfig.java
│       ├── controller/
│       │   ├── CacheController.java DELETE /api/cache/{cacheName}
│       │   ├── ChatController.java
│       │   ├── ConfigController.java
│       │   ├── ConversationController.java  + /export/json, /export/pdf endpoints
│       │   └── UserPreferencesController.java GET/PUT/DELETE /api/preferences/{clientId}
│       ├── dto/
│       │   ├── ChatRequest.java
│       │   ├── ChatResponse.java
│       │   ├── ConversationDTO.java
│       │   ├── MessageDTO.java
│       │   └── UserPreferencesDTO.java
│       ├── filter/
│       │   └── RateLimitFilter.java OncePerRequestFilter — Bucket4j per-IP, configurable limit
│       ├── service/
│       │   ├── AgentCoordinationService.java
│       │   ├── ConversationExportService.java  JSON (Jackson) + PDF (OpenPDF) export
│       │   ├── ConversationService.java        + MetricsService
│       │   ├── MetricsService.java             chat/weather/news counters via MeterRegistry
│       │   ├── OrchestratorService.java
│       │   └── UserPreferencesService.java     getOrCreate, save, delete
│       └── web/
│           └── Application.java
│
└── orchestrator-frontend/
    ├── public/
    │   └── index.html
    └── src/
        ├── index.js
        ├── App.js
        ├── App.css
        ├── components/
        │   ├── ChatInterface.js
        │   ├── ConversationHistory.js
        │   └── ResponseDisplay.js
        └── services/
            └── ApiService.js
```

---

## Key Dependencies

```gradle
// orchestrator-core (api — exposed to consumers)
api 'org.springframework.ai:spring-ai-starter-model-ollama'
api 'org.springframework.ai:spring-ai-starter-model-openai'
api 'org.springframework.ai:spring-ai-starter-model-anthropic'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-validation'
runtimeOnly 'com.h2database:h2'

// orchestrator-mcp (api — exposed to consumers)
api project(':orchestrator-core')
api 'org.springframework.ai:spring-ai-starter-mcp-client'
implementation 'org.springframework.boot:spring-boot-starter-webflux'

// orchestrator-web
implementation project(':orchestrator-core')
implementation project(':orchestrator-mcp')
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
implementation 'org.springframework.boot:spring-boot-starter-cache'
implementation 'com.github.ben-manes.caffeine:caffeine'
implementation 'org.springframework.boot:spring-boot-starter-aspectj'  // AOP — enables @Retryable/@ConcurrencyLimit
implementation 'com.bucket4j:bucket4j-core:8.10.1'
implementation 'com.github.librepdf:openpdf:2.0.3'

// all modules (root subprojects block)
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

---

## Build & Run

```bash
# Prerequisites: start MCP servers
docker compose -f docker-compose-mcp-weather.yml up -d
docker compose -f docker-compose-mcp-news.yml up -d

# Build
./gradlew build -x test

# Run
./gradlew :orchestrator-web:bootRun
```

---

**Last Updated:** 2026-03-11
**Current Phase:** Phase 12 complete — all enhancements delivered
**Build Status:** ✅ `BUILD SUCCESSFUL` — 43 tests pass, full stack verified (Phases 1–12 complete)
