---
name: tk-video-ai-orchestrator
description: 当实现或修改 TikTok Shop AI 视频项目的 Python FastAPI + Temporal AI 编排服务时使用，包括商品分析、视频方案生成、分镜/脚本生成、Prompt 生成、素材生成、质量检查、AI 回调、Schema 校验、模型 Prompt 或 RenderManifest 生成。
---

# TK Video AI Orchestrator（Python AI 编排）

本项目 Python AI 服务开发必须遵循此规则。

## 开发前必读

- 在修改任何 AI 输出、Pydantic 模型、Prompt JSON 契约、校验逻辑、回调载荷或质量检查之前，必须先阅读 `docs/03-ai-output-json-schema.md`。
- 在修改 Java 回调请求之前，必须先阅读 `docs/02-openapi-spec.yaml`。
- 在生成或修改 `RenderManifest` 之前，必须先阅读 `docs/04-render-manifest-schema.md`。
- 仅在需要将 AI 结果映射到持久化业务字段时阅读 `docs/01-database-schema.sql`。

## 服务职责

Python 负责 AI 工作流执行，不负责业务状态。

它可以：

- 分析商品图片和文本。
- 生成商品卖点、痛点、受众、场景与合规提示。
- 生成视频方案。
- 生成英文脚本、文案、标签、分镜与 Prompt。
- 生成或请求 AI 图片和短视频片段。
- 执行质量与合规检查。
- 构建 `RenderManifest`。
- 通过结构化阶段结果回调 Java。

它不得：

- 直接写入 Java 核心业务表。
- 在 Java 回调之外决定用户可见的最终任务状态。
- 编造不存在的产品功效声明。
- 在 AI JSON 输出中返回 Markdown、代码块或解释性文本。

## 输出规则

所有 AI 结构化输出必须：

- 是合法 JSON。
- 匹配对应的 JSON Schema。
- 除非 Schema 显式允许，否则拒绝额外字段。
- 数组为空时返回 `[]`。
- 评分字段保持在 `0-100` 范围内。
- 基于真实商品信息。
- 在要求的位置包含合规风险字段。

## 回调规则

回调 Java 时使用：

- `schemaVersion = "1.0.0"`
- `stage`：`product_analysis`、`video_plan`、`storyboard`、`material`、`quality_check` 或 `render_manifest`
- `status`：`success` 或 `failed`
- `nextTaskStatus`：仅当 Java 需要推进任务状态时携带

失败时包含统一的 `error` 对象：

- `errorCode`
- `errorMessage`
- `failedStage`
- `retryable`
- 可选的 `provider`
- 可选的 `rawError`

## 校验流程

1. 获取模型原始输出。
2. 必要时仅提取 JSON 部分。
3. 使用 JSON Schema 校验。
4. 使用 Pydantic 校验/规范化。
5. 校验失败则尝试修复。
6. 修复失败则重试。
7. 重试耗尽后回调 Java 上报 `failed`。
