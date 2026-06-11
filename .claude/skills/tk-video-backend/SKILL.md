---
name: tk-video-backend
description: 当实现或修改 TikTok Shop AI 视频项目的 Java Spring Boot 后端时使用，包括认证、用户、商品、视频任务、视频方案、分镜、素材、成片、额度、存储、回调、管理后台接口、数据库映射、任务状态流转以及服务间安全。
---

# TK Video Backend（Java 后端）

本项目 Java Spring Boot 后端开发必须遵循此规则。

## 开发前必读

- 在修改持久层、实体、数据库迁移、状态逻辑、额度或日志之前，必须先阅读 `docs/01-database-schema.sql`。
- 在修改 Controller、DTO、回调、请求/响应字段或认证行为之前，必须先阅读 `docs/02-openapi-spec.yaml`。
- 如果涉及 AI 回调载荷，必须同时阅读 `docs/03-ai-output-json-schema.md`。
- 如果涉及渲染任务创建或渲染回调，必须同时阅读 `docs/04-render-manifest-schema.md`。

## 后端职责

Java Spring Boot 是主业务控制器，也是用户可见状态的唯一真实来源。

它拥有：

- 认证、用户角色和用户状态。
- 商品与商品图片记录。
- 视频任务的生命周期与状态流转。
- 视频方案、分镜、素材、最终成片。
- 额度的预扣、退还、授予及幂等性。
- 预签名上传 URL。
- AI 与 Render Worker 的回调。
- 管理后台视图、日志、成本记录、失败任务诊断。

## 实现规则

- 所有用户面的读写操作必须强制校验用户归属。
- 使用 `videoType`，不得使用 `videoStyle`。
- 将 `template` 视为来自 `RenderManifest` 的渲染层数据。
- 校验回调请求中的 `X-Internal-Service-Token`。
- AI 回调必须使用 `schemaVersion + stage + status` 三元组。
- Render 回调在 `status = failed` 时不得要求 `videoId`。
- 将原始 AI 和渲染载荷保留以便管理后台诊断，但仅在管理端暴露原始错误信息。
- 额度记录和回调副作用必须使用幂等键。
- `video_tasks.status` 的转换必须显式控制，不允许外部服务任意赋值。

## 失败处理

使用以下字段持久化失败信息：

- `failed_stage`
- `error_code`
- `error_message`
- `error_retryable`
- `retry_count`

外部回调错误映射到统一的错误结构：

- `errorCode`
- `errorMessage`
- `failedStage`
- `retryable`
- 可选的 `provider`
- 可选的仅管理端可见的 `rawError`
