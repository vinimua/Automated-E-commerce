---
name: tk-video-frontend-workbench
description: Use when implementing or modifying the Next.js React TypeScript frontend for the TikTok Shop AI video project, including the user workbench, auth screens, product upload, video task creation, plan selection, storyboard editing, generation progress, video preview/download, video library, quota display, or admin pages.
---

# TK Video Frontend Workbench

Use this skill for the Next.js frontend. Pair it with the existing `frontend-design` skill when building or styling UI.

## Read First

- Read `docs/02-openapi-spec.yaml` before changing API clients, request/response DTOs, forms, task polling, callback-driven UI state, or admin tables.
- Read `docs/04-render-manifest-schema.md` only when showing render manifest details or debugging render inputs.
- Read `docs/03-ai-output-json-schema.md` only when displaying AI analysis, storyboards, quality checks, or material results.

## Frontend Role

Frontend calls only the Java Spring Boot API.

It must not:

- Call Python AI service directly.
- Call Render Worker directly.
- Mutate task state locally as if it were authoritative.
- Invent enum values not present in OpenAPI.

## UI Flow

V1 primary workflow:

1. Login or register.
2. Create product with image URLs and product info.
3. Create video task with `videoType`, duration, subtitles, and voiceover options.
4. Poll Java task status.
5. Show product analysis and video plans.
6. Let user select one plan.
7. Show generated storyboard and allow safe text edits.
8. Show generation progress through material and render stages.
9. Preview completed video.
10. Export/download video.

## Data Rules

- Use OpenAPI names such as `videoType`, `schemaVersion`, `manifestVersion`, and `nextTaskStatus`.
- Treat `video_tasks.status` from Java as authoritative.
- Show failure details from the shared error shape when available.
- Keep user-facing failure text concise; detailed `rawError` is admin-only.

## Interface Guidance

- This is a workbench, not a marketing landing page.
- Prefer dense, clear operational UI for repeated task management.
- Keep task status, quota usage, retry options, and failure reasons easy to scan.
