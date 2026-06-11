# TikTok Shop AI 带货视频生成系统

> ✅ Phase 1 Complete — Infrastructure & Project Scaffolding

## What's here

```
tk-ai-video/
  apps/
    web/                    # Next.js 14 Frontend (TypeScript, Tailwind, shadcn/ui)
    api-java/               # Spring Boot 3 Backend (Java 21, MyBatis-Plus)
    render-worker/          # Node.js Render Worker (Remotion, FFmpeg, RabbitMQ)
  services/
    ai-orchestrator/        # Python FastAPI + Temporal AI Orchestrator
  infra/
    docker-compose.yml      # PostgreSQL, Redis, RabbitMQ, Temporal
  docs/                     # Contracts: DB schema, OpenAPI, AI schema, RenderManifest
```

## Quick Start

### Prerequisites

- Docker Desktop
- Java 21
- Node.js 20+
- Python 3.12+

### 1. Start infrastructure

```bash
cd infra
docker compose up -d
```

### 2. Start services

Each in its own terminal:

```bash
# Java API (http://localhost:8080)
cd apps/api-java
./gradlew bootRun

# Python AI (http://localhost:8000)
cd services/ai-orchestrator
uv sync && uv run uvicorn src.main:app --reload

# Next.js Frontend (http://localhost:3000)
cd apps/web
npm install && npm run dev

# Render Worker
cd apps/render-worker
npm install && npm run dev
```

### 3. Verify

```bash
# Health checks
curl http://localhost:8080/actuator/health
curl http://localhost:8000/ai/health
curl http://localhost:3000

# RabbitMQ Management UI
open http://localhost:15672  # tk_user / tk_dev_password

# Temporal UI
open http://localhost:8088
```

## Architecture

- **Next.js Web** → only calls **Java API**
- **Java Spring Boot + PostgreSQL** → single source of business truth
- **Python FastAPI + Temporal** → AI orchestration, callbacks Java
- **Node Render Worker** → consumes RabbitMQ, renders Remotion templates, callbacks Java

## V1 Scope

3 video types (phase-freezed for V1):
- `pain_point_solution`
- `before_after`
- `review`

## Contracts

All services follow contract-first development:

| Document | Scope |
|---|---|
| [01-database-schema.sql](docs/01-database-schema.sql) | 14 tables, state machine, enums |
| [02-openapi-spec.yaml](docs/02-openapi-spec.yaml) | REST API & callback contracts |
| [03-ai-output-json-schema.md](docs/03-ai-output-json-schema.md) | AI structured output schemas |
| [04-render-manifest-schema.md](docs/04-render-manifest-schema.md) | Render protocol |

## Development

See [V1-Development-Roadmap.md](docs/V1-Development-Roadmap.md) for the full 6-phase plan.

Use the `Skill` tool with: `tk-video-contracts`, `tk-video-backend`, `tk-video-ai-orchestrator`, `tk-video-render-worker`, `tk-video-frontend-workbench`
