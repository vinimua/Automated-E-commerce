# CLAUDE.md

## Project: TikTok Shop AI 带货视频生成系统

> V1 目标：商品上传 → AI 分析 → 生成方案 → 用户选方案 → 脚本分镜 → AI 素材 → 自动渲染 → 导出 MP4

## Quick Start

```bash
# 1. Start infrastructure
cd infra && docker compose up -d

# 2. Start services (each in its own terminal)
# Java API (port 8080)
cd apps/api-java && ./gradlew bootRun

# Python AI Orchestrator (port 8000)
cd services/ai-orchestrator && uv run uvicorn src.main:app --reload

# Next.js Frontend (port 3000)
cd apps/web && npm run dev

# Render Worker (background)
cd apps/render-worker && npm run dev
```

## Architecture

```
Next.js Web → Java Spring Boot API → PostgreSQL (唯一状态源)
                  ↑ callback              ↑ callback
           Python FastAPI + Temporal   Node Render Worker
           (AI 编排)                   (Remotion + FFmpeg)
```

## Contract Documents (契约优先)

| File | Scope |
|---|---|
| `docs/01-database-schema.sql` | DB schema, state machine, enums |
| `docs/02-openapi-spec.yaml` | REST API boundary, callback contracts |
| `docs/03-ai-output-json-schema.md` | AI structured output schemas |
| `docs/04-render-manifest-schema.md` | Python→Render Worker render protocol |

## Service Boundaries

- **Frontend** → only calls Java API, never Python/Render Worker directly
- **Java + PostgreSQL** → single source of truth for business state
- **Python AI** → calls back Java, never writes DB directly
- **Render Worker** → only understands RenderManifest, never business state

## Development Skills

Use the `Skill` tool with these skill names:
- `tk-video-contracts` — cross-service contract changes
- `tk-video-backend` — Java Spring Boot development
- `tk-video-ai-orchestrator` — Python AI orchestration
- `tk-video-render-worker` — Node Render Worker
- `tk-video-frontend-workbench` — Next.js frontend

## V1 videoType Freeze

Only 3 videoTypes in V1 UI:
- `pain_point_solution` (template: `pain_point_solution_v1`)
- `before_after` (template: `before_after_v1`)
- `review` (template: `review_v1`)

## Key Invariants

- Task state machine follows `01-database-schema.sql` strictly
- Callback handlers must be idempotent
- Quota operations use idempotency_key with UNIQUE constraint
- Passwords hashed with Argon2id
- Cross-service requests carry `taskId`, `correlationId`
- AI callbacks carry `schemaVersion + stage + status`
- RenderManifest uses `manifestVersion: "1.0.0"`
- Error payloads use `{errorCode, errorMessage, failedStage, retryable}`
