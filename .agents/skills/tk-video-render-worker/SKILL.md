---
name: tk-video-render-worker
description: Use when implementing or modifying the Node.js + Remotion + FFmpeg Render Worker for the TikTok Shop AI video project, including RabbitMQ render jobs, RenderManifest validation, Remotion templates, subtitle layout, media download, FFmpeg output, cover generation, COS upload, render callbacks, or render failure handling.
---

# TK Video Render Worker

Use this skill for Node Render Worker work.

## Read First

- Always read `docs/04-render-manifest-schema.md` before changing render input, template props, media handling, subtitles, cover generation, output settings, or callback payloads.
- Read `docs/02-openapi-spec.yaml` before changing render callbacks to Java.
- Read `docs/01-database-schema.sql` only when checking how Java persists render results or logs.

## Worker Role

Render Worker only renders videos.

It may:

- Consume RabbitMQ render jobs.
- Validate `RenderManifest`.
- Download media assets.
- Render Remotion templates.
- Compose subtitles, product image motion, music, and optional voiceover.
- Run FFmpeg for MP4 output.
- Generate cover images.
- Upload MP4 and cover to COS.
- Callback Java with success or failure.

It must not:

- Read or write business tables directly.
- Handle user authorization, quotas, products, or task state.
- Decide Java task status beyond reporting callback result.
- Accept render inputs other than `RenderManifest`.

## RenderManifest Rules

- Require `manifestVersion = "1.0.0"`.
- Use `videoType` for business type and `template` for Remotion template.
- V1 templates are `pain_point_solution_v1`, `before_after_v1`, and `review_v1`.
- Non-`text` assets require `url`.
- `text` assets require `textContent` and do not require `url`.
- Asset duration sum must match manifest `duration` within 1 second.
- Output config controls MP4 format, codec, and bitrate.
- Subtitle safe area must be respected for TikTok UI.

## Failure Rules

Use the shared error object in render callbacks:

- `errorCode`
- `errorMessage`
- `failedStage`
- `retryable`
- optional `provider`
- optional `rawError`

When `status = failed`, do not require `videoId`, `videoUrl`, or `coverUrl`.
