---
name: tk-video-contracts
description: 当处理 TikTok Shop AI 视频项目的契约、跨服务接口、数据库 Schema、OpenAPI、AI 输出 Schema、RenderManifest、任务状态流转、回调、额度，或任何影响多个服务的变更时使用。
---

# TK Video Contracts（总契约）

本项目采用契约优先。在实现或修改跨服务边界的行为之前，必须先阅读 `docs/` 中对应的契约文件。

## 契约来源

- 数据库与主状态源：`docs/01-database-schema.sql`
- Java API 与回调接口：`docs/02-openapi-spec.yaml`
- Python AI 结构化输出：`docs/03-ai-output-json-schema.md`
- Python 到 Render Worker 的渲染协议：`docs/04-render-manifest-schema.md`

## 必须遵守的工作流

1. 确定受影响的服务边界。
2. 在修改代码之前，先阅读对应的契约文件。
3. 除非对应的契约文件已经更新或在同一次变更中同步更新，否则不得在代码中新增字段、状态、枚举值或回调结构。
4. 数据库、OpenAPI、AI Schema、RenderManifest 中的命名必须保持一致。
5. 如果修改了某个契约，必须检查所有消费该契约的下游服务。

## 不可协商的不变量

- Java Spring Boot + PostgreSQL 是唯一的主业务状态源。
- 前端只调用 Java API，不得直接调用 Python AI 服务或 Render Worker。
- Python AI 服务不得直接写入 Java 核心业务表。
- Render Worker 不得理解用户、额度、商品或任务业务状态。它只消费 `RenderManifest` 并通过 Java 回调接口上报结果。
- `videoType` 是业务视频类型，例如 `pain_point_solution`。
- `template` 是渲染模板，例如 `pain_point_solution_v1`。
- AI 回调使用 `schemaVersion + stage + status` 三元组。
- RenderManifest 固定 `manifestVersion = "1.0.0"`。
- 失败载荷必须使用 `errorCode`、`errorMessage`、`failedStage`、`retryable` 四元组。

## 常见检查项

- 任务状态必须遵循 `01-database-schema.sql` 中定义的状态流转。
- 回调处理器必须幂等。
- 额度的消费/退还/授予记录必须使用幂等键。
- 失败回调不得要求仅在成功时才存在的字段（如 `videoId` 或素材 `url`）。
- AI 生成的 JSON 必须在回调之前通过 Schema 校验。
