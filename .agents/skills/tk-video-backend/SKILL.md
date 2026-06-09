---
name: tk-video-backend
description: Use when implementing or modifying the Java Spring Boot backend for the TikTok Shop AI video project, including auth, users, products, video tasks, video plans, storyboards, materials, videos, quotas, storage, callbacks, admin APIs, database mappings, task status transitions, and service-to-service security.
---

# TK Video Backend

Use this skill for Java Spring Boot backend work in this project.

## Read First

- Always read `docs/01-database-schema.sql` before changing persistence, entities, migrations, status logic, quotas, or logs.
- Always read `docs/02-openapi-spec.yaml` before changing controllers, DTOs, callbacks, request/response fields, or auth behavior.
- If AI callback payloads are involved, also read `docs/03-ai-output-json-schema.md`.
- If render task creation or render callbacks are involved, also read `docs/04-render-manifest-schema.md`.

## Backend Role

Java Spring Boot is the main business controller and the only source of truth for user-visible state.

It owns:

- Auth, user roles, and user status.
- Product and product image records.
- Video task lifecycle and status transitions.
- Video plans, storyboards, materials, final videos.
- Quota pre-deduction, refund, grants, and idempotency.
- Presigned upload URLs.
- AI and Render Worker callbacks.
- Admin views, logs, cost records, failed task diagnostics.

## Implementation Rules

- Enforce user ownership on every user-facing read/write.
- Use `videoType`, not `videoStyle`.
- Treat `template` as render-layer data from `RenderManifest`.
- Validate callback `X-Internal-Service-Token`.
- AI callbacks must use `schemaVersion + stage + status`.
- Render callbacks must not require `videoId` when `status = failed`.
- Store raw AI and render payloads where useful for admin diagnostics, but expose raw errors only to admin surfaces.
- Use idempotency keys for quota records and callback side effects.
- Keep `video_tasks.status` transitions explicit; do not allow arbitrary status assignment from external services.

## Failure Handling

Persist failures using:

- `failed_stage`
- `error_code`
- `error_message`
- `error_retryable`
- `retry_count`

External callback errors map to the shared error shape:

- `errorCode`
- `errorMessage`
- `failedStage`
- `retryable`
- optional `provider`
- optional admin-only `rawError`
