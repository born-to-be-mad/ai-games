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

- **Java 25**
- **Spring Boot 3.4.1**
- **Spring AI 1.0.0-M4**
- **Gradle 9.2**
- **H2 Database** (file-based)
- **React** (frontend)
- **Docker Compose**

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

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew :orchestrator-web:bootRun
```

## Project Status

**Phase 1: Project Foundation** ✅ Complete
- Multi-module Gradle structure
- Spring Boot configuration
- Basic application skeleton

**Next Phases:**
- Phase 2: Core Domain & H2 Integration
- Phase 3: AI Provider Configuration
- Phase 4-5: MCP Server Setup
- Phase 6-12: Agent Implementation, REST API, Frontend, Docker Compose

## License

Copyright © 2026 Daily Context AI. All rights reserved.
