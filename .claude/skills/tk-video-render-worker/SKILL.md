---
name: tk-video-render-worker
description: 当实现或修改 TikTok Shop AI 视频项目的 Node.js + Remotion + FFmpeg 渲染 Worker 时使用，包括 RabbitMQ 渲染任务、RenderManifest 校验、Remotion 模板、字幕布局、素材下载、FFmpeg 输出、封面生成、COS 上传、渲染回调或渲染失败处理。
---

# TK Video Render Worker（Node 渲染 Worker）

本项目 Node Render Worker 开发必须遵循此规则。

## 开发前必读

- 在修改渲染输入、模板属性、素材处理、字幕、封面生成、输出设置或回调载荷之前，必须先阅读 `docs/04-render-manifest-schema.md`。
- 在修改对 Java 的渲染回调之前，必须先阅读 `docs/02-openapi-spec.yaml`。
- 仅在需要了解 Java 如何持久化渲染结果或日志时阅读 `docs/01-database-schema.sql`。

## Worker 职责

Render Worker 只负责渲染视频。

它可以：

- 消费 RabbitMQ 渲染任务。
- 校验 `RenderManifest`。
- 下载素材文件。
- 渲染 Remotion 模板。
- 合成字幕、商品图片动效、背景音乐以及可选配音。
- 使用 FFmpeg 输出 MP4。
- 生成封面图片。
- 将 MP4 和封面上传到 COS。
- 回调 Java 上报成功或失败。

它不得：

- 直接读写业务表。
- 处理用户授权、额度、商品或任务状态。
- 在回调结果之外决定 Java 的任务状态。
- 接受 `RenderManifest` 以外的任何渲染输入。

## RenderManifest 规则

- 必须要求 `manifestVersion = "1.0.0"`。
- 使用 `videoType` 表示业务类型，使用 `template` 表示 Remotion 模板。
- V1 模板为 `pain_point_solution_v1`、`before_after_v1` 和 `review_v1`。
- 非 `text` 素材必须提供 `url`。
- `text` 素材必须提供 `textContent`，且不要求 `url`。
- 所有素材的 duration 之和必须与 manifest 的 `duration` 一致（误差不超过 1 秒）。
- 输出配置控制 MP4 格式、编码和码率。
- 字幕安全区域必须为 TikTok UI 保留足够空间。

## 失败规则

在渲染回调中使用统一的错误对象：

- `errorCode`
- `errorMessage`
- `failedStage`
- `retryable`
- 可选的 `provider`
- 可选的 `rawError`

当 `status = failed` 时，不得要求 `videoId`、`videoUrl` 或 `coverUrl`。
