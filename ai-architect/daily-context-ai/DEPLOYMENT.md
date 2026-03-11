# Deployment Guide

## Prerequisites

| Tool           | Version  | Notes                                    |
|----------------|----------|------------------------------------------|
| Docker         | 24+      | Required for Docker Compose deployment   |
| Docker Compose | v2+      | Uses `docker compose` (no hyphen)        |
| Java           | 25       | Only needed for local Gradle runs        |
| Node.js        | 20+      | Only needed for local frontend runs      |
| Ollama         | latest   | Only needed if **not** using `--profile ollama` |

---

## Quick Start (Docker Compose)

### 1. Configure environment

```bash
cp .env.example .env
# Open .env and fill in the required keys (see README for key acquisition guide)
```

Minimum `.env` for Ollama + Open-Meteo (no paid keys needed):

```dotenv
AI_PROVIDER_ACTIVE=ollama
OLLAMA_BASE_URL=http://host.docker.internal:11434   # local Ollama
```

### 2. Start all services

**Option A — Local Ollama (recommended for first run)**

Make sure Ollama is running locally and has `llama3.2` pulled:

```bash
ollama pull llama3.2
docker compose up --build
```

**Option B — Ollama in Docker** (downloads ~3GB model on first run)

```bash
docker compose --profile ollama up --build
```

### 3. Verify

```bash
curl http://localhost:8080/api/config/providers
# → ["ollama"]

curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"Weather in London?"}'

open http://localhost:3000          # React UI
```

### 4. Stop

```bash
docker compose down                              # keep Ollama volume
docker compose --profile ollama down -v          # also removes ollama-models volume
```

---

## Service Map

| Service                   | Internal host                    | External port |
|---------------------------|----------------------------------|---------------|
| Backend (Spring Boot)     | `backend:8080`                   | `8080`        |
| Frontend (Nginx)          | `frontend:80`                    | `3000`        |
| Weather MCP — Open-Meteo  | `weather-mcp-openmeteo:8080`     | `8101`        |
| Weather MCP — WeatherAPI  | `weather-mcp-weatherapi:8080`    | `8103`        |
| Weather MCP — OpenWeather | `weather-mcp-openweathermap:8080`| `8104`        |
| News MCP                  | `news-mcp:8080`                  | `8102`        |
| Ollama (optional profile) | `ollama:11434`                   | `11434`       |

---

## Environment Variables

### AI Provider

| Variable            | Default    | Description                               |
|---------------------|------------|-------------------------------------------|
| `AI_PROVIDER_ACTIVE`| `ollama`   | Active LLM: `ollama`, `openai`, `anthropic` |
| `OLLAMA_BASE_URL`   | `http://localhost:11434` | Ollama base URL (override for local Ollama) |
| `OPENAI_API_KEY`    | *(empty)*  | OpenAI key — required when provider=openai |
| `ANTHROPIC_API_KEY` | *(empty)*  | Anthropic key — required when provider=anthropic |

### Weather MCP

| Variable                    | Default               | Description                  |
|-----------------------------|-----------------------|------------------------------|
| `WEATHERAPI_KEY`            | *(empty)*             | WeatherAPI.com key           |
| `OPENWEATHERMAP_KEY`        | *(empty)*             | OpenWeatherMap key           |
| `MCP_WEATHER_OPENMETEO_URL` | `http://localhost:8101` | Internal URL (Docker sets automatically) |
| `MCP_WEATHER_WEATHERAPI_URL`| `http://localhost:8103` | Internal URL (Docker sets automatically) |
| `MCP_WEATHER_OPENWEATHERMAP_URL` | `http://localhost:8104` | Internal URL (Docker sets automatically) |

### News MCP

| Variable              | Default               | Description        |
|-----------------------|-----------------------|--------------------|
| `THENEWSAPI_KEY`      | *(empty)*             | TheNewsAPI key     |
| `GNEWS_KEY`           | *(empty)*             | GNews key          |
| `NEWSAPI_KEY`         | *(empty)*             | NewsAPI.org key    |
| `MCP_NEWS_AGGREGATOR_URL` | `http://localhost:8102` | Internal URL (Docker sets automatically) |

---

## Local Development (without Docker)

### Backend

```bash
# Start MCP servers first (each in its own terminal):
cd docker/weather-mcp-openmeteo && pip install -r requirements.txt && python server.py
cd docker/weather-mcp-weatherapi && pip install -r requirements.txt && WEATHERAPI_KEY=<key> python server.py
cd docker/weather-mcp-openweathermap && pip install -r requirements.txt && OPENWEATHERMAP_KEY=<key> python server.py
cd docker/news-mcp && pip install -r requirements.txt && THENEWSAPI_KEY=<key> GNEWS_KEY=<key> NEWSAPI_KEY=<key> python server.py

# Then start the Spring Boot backend:
./gradlew :orchestrator-web:bootRun
```

Backend starts at `http://localhost:8080`.

### Frontend

```bash
cd orchestrator-frontend
npm install
npm start
```

Frontend starts at `http://localhost:3000` and proxies `/api/*` to `localhost:8080`.

---

## Rebuilding Individual Services

```bash
# Rebuild and restart backend only
docker compose build backend
docker compose up -d backend

# Rebuild and restart a specific MCP server
docker compose build news-mcp
docker compose up -d news-mcp

# Rebuild frontend
docker compose build frontend
docker compose up -d frontend
```

---

## Data Persistence

The H2 database is stored in `./data/orchestrator.mv.db`. This directory is mounted as a volume in Docker:

```yaml
volumes:
  - ./data:/app/data
```

To reset the database: `rm -rf ./data/`

---

## Switching AI Providers

Edit `.env` and restart the backend:

```dotenv
# Switch to OpenAI
AI_PROVIDER_ACTIVE=openai
OPENAI_API_KEY=sk-proj-...

# Switch to Anthropic
AI_PROVIDER_ACTIVE=anthropic
ANTHROPIC_API_KEY=sk-ant-...
```

```bash
docker compose restart backend
```
