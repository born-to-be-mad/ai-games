# Troubleshooting Guide

---

## Startup Issues

### Backend fails to start — `Connection refused` to MCP server

**Symptom:** Backend logs show `Could not connect to MCP server at http://localhost:8101` or similar.

**Cause:** MCP server container hasn't finished starting or healthcheck hasn't passed yet.

**Fix:**
```bash
# Check MCP service status
docker compose ps

# Check a specific service's logs
docker compose logs weather-mcp-openmeteo

# Wait for all services to be healthy
docker compose up --wait
```

---

### Backend fails to start — `No qualifying bean of type ChatModel`

**Symptom:** Spring context fails with `No qualifying bean of type 'ChatModel' available`.

**Cause:** The configured AI provider (e.g. `ollama`) has no reachable model server, so the auto-configuration skips registering the bean.

**Fix:**
1. Verify `AI_PROVIDER_ACTIVE` in `.env` matches an available provider.
2. Check Ollama is running: `ollama list` or `curl http://localhost:11434`.
3. If using `--profile ollama`, check the Ollama container: `docker compose logs ollama`.

---

### Port 8080 already in use

**Symptom:** `bind: address already in use` or `Ports are not available: exposing port TCP 0.0.0.0:8080`.

**Fix:**
```bash
# Find the process using port 8080
lsof -i :8080
# Kill it
kill <PID>
# Or change the backend port in docker-compose.yml:
# ports: - "8081:8080"
```

---

### Frontend Nginx shows `502 Bad Gateway` for `/api/*`

**Symptom:** The React UI loads but all API calls return 502.

**Cause:** Nginx can't reach the `backend` service, likely because the backend container isn't healthy yet.

**Fix:**
```bash
docker compose ps
docker compose logs backend | tail -20
# Wait for backend healthcheck to pass, then reload the page
```

---

## MCP Server Issues

### Weather MCP returns empty results

**Symptom:** Weather queries return "I couldn't retrieve weather data" or no data.

**Possible causes and fixes:**

1. **Missing API key** — Check `.env` has `WEATHERAPI_KEY` or `OPENWEATHERMAP_KEY` set.
2. **API key not yet active** — OpenWeatherMap keys can take up to 10 minutes to activate after registration.
3. **Free plan exhausted** — The free tier for WeatherAPI is 1M calls/month; OpenWeatherMap is 1,000/day. Check your dashboard.
4. **Open-Meteo** — This provider is always free and requires no key. If it's also failing, check MCP connectivity:
   ```bash
   curl http://localhost:8101/mcp/
   ```

---

### News MCP returns no articles

**Symptom:** News queries return empty results.

**Possible causes and fixes:**

1. **Missing API keys** — At least one of `THENEWSAPI_KEY`, `GNEWS_KEY`, or `NEWSAPI_KEY` must be set.
2. **NewsAPI free plan restriction** — The `newsapi.org` free plan only works from `localhost`. From Docker (non-localhost), you need a paid plan. Use `GNEWS_KEY` or `THENEWSAPI_KEY` instead.
3. **Rate limit hit** — Free tiers are 100 requests/day. Restart the next day or use a different provider key.

---

### MCP server crashes on startup — `TypeError: FastMCP.run()`

**Symptom:** MCP container exits with `TypeError: FastMCP.run() got an unexpected keyword argument 'host'`.

**Cause:** This was fixed in Phase 11. If you see this with a fresh clone, it means you have an old version of the `server.py` files.

**Fix:** Verify the `server.py` files use the pattern:
```python
mcp = FastMCP("name", host="0.0.0.0", port=8080)
mcp.run(transport="streamable-http")
```

---

## AI Provider Issues

### Ollama — model not found

**Symptom:** Backend logs show `model 'llama3.2' not found` or similar.

**Fix:**
```bash
# If using local Ollama
ollama pull llama3.2

# If using Docker Ollama (--profile ollama)
docker compose exec ollama ollama pull llama3.2
```

---

### OpenAI — 401 Unauthorized

**Symptom:** Response includes `401 Unauthorized` or `Incorrect API key`.

**Fix:**
1. Verify `OPENAI_API_KEY` in `.env` starts with `sk-proj-` (new format) or `sk-` (legacy).
2. Check key hasn't expired: [OpenAI API keys dashboard](https://platform.openai.com/api-keys).
3. Ensure billing is set up on your OpenAI account.

---

### Anthropic — 401 Unauthorized

**Symptom:** Response includes `authentication_error`.

**Fix:**
1. Verify `ANTHROPIC_API_KEY` in `.env` starts with `sk-ant-`.
2. Check key status: [Anthropic console](https://console.anthropic.com/).

---

### Slow responses (30s+)

**Symptom:** Queries take very long to respond, especially with Ollama.

**Cause:** Ollama runs the model on CPU when no GPU is available.

**Fix options:**
- Switch to OpenAI or Anthropic for faster cloud inference.
- If you have a GPU, ensure Ollama is using it: `ollama ps` should show GPU usage.
- Reduce context/model size: edit `application.yml` to use a smaller model (e.g. `llama3.2:1b`).

---

## Database Issues

### H2 console not loading in Docker

**Symptom:** `http://localhost:8080/h2-console` doesn't load or redirects to `/login`.

**Cause:** H2 console is only accessible via the Nginx proxy at `http://localhost:3000/h2-console` when using Docker, or directly at `http://localhost:8080/h2-console` when port 8080 is exposed.

**Fix:** Access via `http://localhost:8080/h2-console` — the Docker Compose setup exposes port 8080 directly.

JDBC URL: `jdbc:h2:file:./data/orchestrator` — username `sa`, no password.

---

### Database locked / locked by another process

**Symptom:** Backend fails with `org.h2.jdbc.JdbcSQLException: Database may be already in use`.

**Cause:** Another process (e.g. a previous local run) holds the H2 file lock.

**Fix:**
```bash
# Find the process locking the file
lsof | grep orchestrator
kill <PID>
# Or simply restart Docker containers
docker compose restart backend
```

---

## Diagnostic Commands

```bash
# Check all service health
docker compose ps

# View backend logs (live)
docker compose logs -f backend

# View all logs
docker compose logs --tail=50

# Inspect a specific MCP endpoint
curl -v http://localhost:8101/mcp/

# Test the backend API directly
curl -s http://localhost:8080/api/config/providers

# Test end-to-end chat
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"Hello, what can you do?"}' | jq .

# Check which Ollama models are available
curl http://localhost:11434/api/tags | jq '.models[].name'
```
