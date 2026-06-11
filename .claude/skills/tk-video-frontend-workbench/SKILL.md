---
name: tk-video-frontend-workbench
description: 当实现或修改 TikTok Shop AI 视频项目的 Next.js React TypeScript 前端时使用，包括用户工作台、认证页面、商品上传、视频任务创建、方案选择、分镜编辑、生成进度、视频预览/下载、视频库、额度展示或管理后台页面。
---

# TK Video Frontend Workbench（前端工作台）

本项目 Next.js 前端开发必须遵循此规则。在构建或美化 UI 时搭配 `frontend-design` Skill 一起使用。

## 开发前必读

- 在修改 API 客户端、请求/响应 DTO、表单、任务轮询、回调驱动的 UI 状态或管理后台表格之前，必须先阅读 `docs/02-openapi-spec.yaml`。
- 仅在展示渲染清单详情或调试渲染输入时阅读 `docs/04-render-manifest-schema.md`。
- 仅在展示 AI 分析、分镜、质量检查或素材结果时阅读 `docs/03-ai-output-json-schema.md`。

## 前端职责

前端只调用 Java Spring Boot API。

它不得：

- 直接调用 Python AI 服务。
- 直接调用 Render Worker。
- 在本地修改任务状态并视其为权威状态。
- 自行发明 OpenAPI 中不存在的枚举值。

## UI 流程

V1 主要工作流：

1. 登录或注册。
2. 创建商品，上传商品图片和商品信息。
3. 创建视频任务，选择 `videoType`、时长、字幕和配音选项。
4. 轮询 Java 任务状态。
5. 查看商品分析与视频方案。
6. 让用户选择一个方案。
7. 查看生成的分镜，允许安全地编辑文本内容。
8. 在素材生成和渲染阶段查看生成进度。
9. 预览成片。
10. 导出/下载视频。

## 数据规则

- 使用 OpenAPI 中定义的字段名，如 `videoType`、`schemaVersion`、`manifestVersion`、`nextTaskStatus`。
- 以 Java 返回的 `video_tasks.status` 为权威状态。
- 在可用时展示统一错误结构中的失败详情。
- 用户面失败文案保持简洁；详细的 `rawError` 仅限管理端展示。

## 界面指导

- 这是一个工作台，不是营销落地页。
- 倾向使用信息密集、清晰的操作型 UI，适合频繁的任务管理。
- 任务状态、额度使用、重试选项和失败原因应一目了然。
