# API Documentation

Base URL: `http://localhost:8080`

---

## Endpoints

### Chat

#### `POST /api/chat`

Submit a query to the AI orchestrator. The assistant analyzes intent, dispatches weather and/or news agents as needed, and synthesizes a final response.

**Request body**

| Field              | Type          | Required | Description                                         |
|--------------------|---------------|----------|-----------------------------------------------------|
| `query`            | `string`      | yes      | Natural-language question (must not be blank)       |
| `conversationId`   | `UUID`        | no       | Existing conversation to continue; creates new if absent |
| `weatherProviders` | `Set<string>` | no       | Subset of weather providers to use (empty = all)    |
| `newsSources`      | `Set<string>` | no       | Subset of news sources to use (empty = all)         |

**Response**

| Field            | Type     | Description                                  |
|------------------|----------|----------------------------------------------|
| `conversationId` | `UUID`   | Conversation ID (new or existing)            |
| `answer`         | `string` | Synthesized natural-language answer          |
| `durationMs`     | `long`   | Time taken to generate the answer in ms      |

**Examples**

```bash
# New conversation — general question
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"What is the weather in Warsaw today?"}' | jq .

# Continue an existing conversation
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"What about tomorrow?","conversationId":"<uuid>"}' | jq .

# Request with specific provider subset
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"Latest tech news","newsSources":["newsapi","gnews"]}' | jq .
```

---

### Conversations

#### `GET /api/conversations`

List all conversations, ordered by newest first.

**Response** — array of conversation summaries

| Field       | Type            | Description                          |
|-------------|-----------------|--------------------------------------|
| `id`        | `UUID`          | Conversation identifier              |
| `timestamp` | `LocalDateTime` | Creation time                        |
| `topic`     | `string`        | Auto-derived topic (first 50 chars)  |
| `messages`  | `array`         | Always empty in list view            |

```bash
curl -s http://localhost:8080/api/conversations | jq .
```

---

#### `GET /api/conversations/{id}`

Get a single conversation with its full message history.

**Path parameter:** `id` — conversation UUID

**Response** — conversation with `messages` populated

| Message field | Type            | Description                   |
|---------------|-----------------|-------------------------------|
| `id`          | `UUID`          | Message identifier            |
| `role`        | `string`        | `USER` or `ASSISTANT`         |
| `content`     | `string`        | Message text                  |
| `timestamp`   | `LocalDateTime` | Message creation time         |

```bash
curl -s http://localhost:8080/api/conversations/<uuid> | jq .
```

**Errors**

| Status | Condition                     |
|--------|-------------------------------|
| `404`  | Conversation not found        |

---

#### `DELETE /api/conversations/{id}`

Delete a conversation and all its messages.

**Path parameter:** `id` — conversation UUID

**Response:** `204 No Content`

```bash
curl -s -X DELETE http://localhost:8080/api/conversations/<uuid>
```

**Errors**

| Status | Condition                     |
|--------|-------------------------------|
| `404`  | Conversation not found        |

---

### Configuration

#### `GET /api/config/providers`

List all AI providers that are currently configured and available.

**Response** — array of provider name strings (e.g. `["ollama","openai","anthropic"]`)

```bash
curl -s http://localhost:8080/api/config/providers | jq .
```

---

### Developer Tools

#### H2 Console

Available at `http://localhost:8080/h2-console` (local dev only).

| Setting        | Value                            |
|----------------|----------------------------------|
| Driver Class   | `org.h2.Driver`                  |
| JDBC URL       | `jdbc:h2:file:./data/orchestrator` |
| Username       | `sa`                             |
| Password       | *(empty)*                        |

---

## Error Handling

All endpoints return standard Spring error responses:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Conversation not found: <uuid>",
  "path": "/api/conversations/<uuid>"
}
```

| Status | Meaning                                      |
|--------|----------------------------------------------|
| `200`  | Success                                      |
| `204`  | Success (no body — DELETE)                   |
| `400`  | Validation error (e.g. blank `query`)        |
| `404`  | Resource not found                           |
| `500`  | Internal server error (check logs)           |
