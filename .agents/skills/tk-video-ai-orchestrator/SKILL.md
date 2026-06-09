---
name: tk-video-ai-orchestrator
description: Use when implementing or modifying the Python FastAPI + Temporal AI orchestration service for the TikTok Shop AI video project, including product analysis, video plan generation, storyboard/script generation, prompt generation, material generation, quality checks, AI callbacks, schema validation, model prompts, or RenderManifest generation.
---

# TK Video AI Orchestrator

Use this skill for Python AI service work.

## Read First

- Read `docs/03-ai-output-json-schema.md` before changing any AI output, Pydantic model, prompt JSON contract, validation, callback payload, or quality check.
- Read `docs/02-openapi-spec.yaml` before changing Java callback requests.
- Read `docs/04-render-manifest-schema.md` before generating or modifying `RenderManifest`.
- Read `docs/01-database-schema.sql` only when mapping AI results to persisted business fields.

## Service Role

Python owns AI workflow execution, not business state.

It may:

- Analyze product images and text.
- Generate product selling points, pain points, audience, scenes, and compliance notes.
- Generate video plans.
- Generate English scripts, captions, hashtags, storyboards, and prompts.
- Generate or request AI images and short clips.
- Run quality and compliance checks.
- Build `RenderManifest`.
- Callback Java with structured stage results.

It must not:

- Write Java core business tables directly.
- Decide final user-visible task state outside Java callbacks.
- Invent unsupported product claims.
- Return Markdown, code fences, or explanatory text in AI JSON outputs.

## Output Rules

All AI structured outputs must:

- Be valid JSON.
- Match the relevant JSON Schema.
- Reject additional fields unless the schema explicitly allows them.
- Include arrays as `[]` when empty.
- Keep score fields in `0-100`.
- Be based on real product information.
- Include compliance risk fields where required.

## Callback Rules

Callbacks to Java use:

- `schemaVersion = "1.0.0"`
- `stage`: `product_analysis`, `video_plan`, `storyboard`, `material`, `quality_check`, or `render_manifest`
- `status`: `success` or `failed`
- `nextTaskStatus` only when Java should advance the task.

On failure, include the shared `error` object:

- `errorCode`
- `errorMessage`
- `failedStage`
- `retryable`
- optional `provider`
- optional `rawError`

## Validation Workflow

1. Get raw model output.
2. Extract JSON only if necessary.
3. Validate with JSON Schema.
4. Validate/normalize with Pydantic.
5. Repair or retry if validation fails.
6. Callback Java with `failed` after retry exhaustion.
