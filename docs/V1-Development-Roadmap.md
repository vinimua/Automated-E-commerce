# TikTok Shop AI 带货视频生成系统 V1 开发任务路线

> 版本：1.0.1
> 日期：2026-06-09
> 状态：规划完成，待执行；已补充 V1 风险收敛约束

---

## 文档说明

本文档基于以下两份 V1 设计文档制定：

- `TikTok Shop AI 带货视频生成系统 V1 产品需求文档.docx`
- `TikTok Shop AI 带货视频生成系统 V1 技术方案文档.docx`

并受以下四份契约文件约束：

- `01-database-schema.sql` — 数据库与主状态源
- `02-openapi-spec.yaml` — API 边界与回调契约
- `03-ai-output-json-schema.md` — AI 结构化输出约束
- `04-render-manifest-schema.md` — Python → Render Worker 渲染协议

---

## V1 目标

打通一条完整闭环：

```
商品上传 → AI 分析 → 生成方案 → 用户选方案 → 脚本分镜 → AI 素材 → 自动渲染 → 导出 MP4
```

V1 只追求**稳定生成可发布的 TikTok 带货视频**，不涉及批量生成、自动发布、数据分析等 V2+ 功能。

### V1 范围冻结

为降低跨服务返工，V1 首版只开放 3 种 `videoType`：

| videoType | Render template |
|---|---|
| `pain_point_solution` | `pain_point_solution_v1` |
| `before_after` | `before_after_v1` |
| `review` | `review_v1` |

`product_showcase`、`ugc_style`、`tutorial` 已保留在契约中，但 V1 UI 默认隐藏，Java 创建任务时暂不允许普通用户选择；若要提前开放，必须同步补齐 OpenAPI、数据库校验、RenderManifest 映射、Remotion 模板和端到端测试。

---

## 服务架构

```
Next.js Web (前端)
     │ HTTP JWT
     ▼
Java Spring Boot API (主业务后端 + PostgreSQL 唯一状态源)
     │ callback              │ callback
     ▼                       ▼
Python FastAPI + Temporal   Node Render Worker
(AI 编排, 不碰业务状态)      (Remotion + FFmpeg, 只消费 RenderManifest)
```

关键边界约束：

- 前端**只**调 Java API，不直连 Python / Render Worker
- Java + PostgreSQL 是**唯一**主状态源
- Python 通过回调通知 Java，不直接写业务表
- Render Worker **只**理解 RenderManifest，不理解用户/商品/额度/任务状态
- 回调鉴权使用 `X-Internal-Service-Token`

---

## 总体阶段划分

```
阶段 1（基础设施）
  │
  ▼
阶段 2（Java 后端核心）──────┐
  │                          │
  ▼                          ▼
阶段 3（前端前半段）    阶段 4（Python AI 编排）
  │                          │
  │                          ▼
  │                    阶段 5（Node Render Worker）
  │                          │
  └──────────┬───────────────┘
             ▼
      阶段 6（前端收尾 + 管理后台 + 联调）
```

- 阶段 3 与阶段 4 **可并行**，但前提是阶段 2 先完成“契约冻结 + mock server + fixture”里程碑
- 阶段 5 **必须在阶段 4 之后**（依赖 RenderManifest 产出）
- 所有阶段开发前需阅读对应契约文件（4 个 docs）

---

## 阶段 1：基础设施与工程脚手架

**目标**：所有服务可本地启动，Docker Compose 一键拉起基础设施。

### 1.1 Monorepo 目录结构

| 任务 | 服务 | 产出物 |
|---|---|---|
| 创建项目根目录骨架 | 全局 | `apps/web/`, `apps/api-java/`, `apps/render-worker/`, `services/ai-orchestrator/`, `infra/` |

目录结构：

```
tk-ai-video/
  apps/
    web/                       # Next.js 前端
    api-java/                  # Spring Boot 后端
    render-worker/             # Node Render Worker
  services/
    ai-orchestrator/           # Python FastAPI + Temporal
  infra/
    docker-compose.yml
    postgres/
    redis/
    rabbitmq/
    temporal/
  docs/
    V1-Development-Roadmap.md  # 本文档
    01-database-schema.sql
    02-openapi-spec.yaml
    03-ai-output-json-schema.md
    04-render-manifest-schema.md
    ... (需求/技术文档)
  .claude/
    skills/                    # 5 个开发 Skill
```

### 1.2 Docker Compose 基础设施

| 服务 | 用途 | 端口 |
|---|---|---|
| PostgreSQL | 主数据库 | 5432 |
| Redis | 缓存/临时状态 | 6379 |
| RabbitMQ | 渲染任务队列 | 5672 / 15672 |
| Temporal Server | AI 工作流编排 | 7233 |
| Temporal UI | 工作流可视化 | 8088 |

### 1.3 项目初始化

| # | 任务 | 服务 | 产出物 |
|---|---|---|---|
| 1.3.1 | Spring Boot 3 项目初始化 (Java 21, MyBatis-Plus, Spring Security, JWT) | api-java | 可启动空项目 |
| 1.3.2 | 执行 `01-database-schema.sql`，Flyway 迁移建 14 张表 | api-java | 数据库就绪 |
| 1.3.3 | Next.js 项目初始化 (TypeScript, Tailwind CSS, shadcn/ui) | web | 可启动前端壳 |
| 1.3.4 | Python FastAPI 项目初始化 + Temporal SDK 连接 | ai-orchestrator | 可启动 Python 服务 |
| 1.3.5 | Node Render Worker 项目初始化 (TypeScript, Remotion, RabbitMQ client) | render-worker | 可启动 Node 服务 |
| 1.3.6 | 环境变量配置、`CLAUDE.md` 项目说明 | 全局 | 新开发者一天跑通 |

---

## 阶段 2：Java 后端核心——用户、商品、任务

**目标**：前端能注册登录、上传商品、创建视频任务、查询状态。Java 后端承载全部业务逻辑。

### 2.1 Auth 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.1.1 | `POST /api/auth/register` | 用户注册 (email + password) |
| 2.1.2 | `POST /api/auth/login` | 用户登录，返回 JWT accessToken + refreshToken |
| 2.1.3 | `POST /api/auth/refresh` | 刷新 accessToken |
| 2.1.4 | Spring Security 配置 | JWT 中间件、角色校验、内部服务 Token 校验 |

### 2.2 User 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.2.1 | 用户信息查询 | 当前用户信息 |
| 2.2.2 | Admin 用户列表 | 管理员查看全部用户 |

### 2.3 Product 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.3.1 | `POST /api/products` | 创建商品 (name, imageUrls, targetMarket, language 等) |
| 2.3.2 | `GET /api/products/{productId}` | 查询商品详情 (含 AI 分析结果) |
| 2.3.3 | `PATCH /api/products/{productId}` | 更新商品信息 (卖点、痛点、受众等) |
| 2.3.4 | `GET /api/products` | 商品列表 (分页) |

### 2.4 VideoTask 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.4.1 | `POST /api/video-tasks` | 创建视频任务 (productId, duration, videoType, needSubtitles, needVoiceover)，预扣 1 次成片额度 |
| 2.4.2 | `GET /api/video-tasks/{taskId}` | 查询任务状态和详情 |
| 2.4.3 | `GET /api/video-tasks` | 任务列表 (支持 status/productId/分页筛选) |
| 2.4.4 | `GET /api/video-tasks/{taskId}/plans` | 获取 AI 生成的视频方案 |
| 2.4.5 | `POST /api/video-tasks/{taskId}/select-plan` | 用户选择一个视频方案 |
| 2.4.6 | `POST /api/video-tasks/{taskId}/retry` | 重试失败任务 |

### 2.5 Storyboard 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.5.1 | `GET /api/video-tasks/{taskId}/storyboard` | 按任务查询分镜 |
| 2.5.2 | `PATCH /api/storyboards/{storyboardId}` | 更新分镜文本字段 (title, hook, caption, hashtags, shots) |

### 2.6 Video 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.6.1 | `GET /api/videos` | 视频列表 (分页，按 productId/status 筛选) |
| 2.6.2 | `GET /api/videos/{videoId}` | 视频详情 |
| 2.6.3 | `POST /api/videos/{videoId}/export` | 标记已导出，返回下载 URL |

### 2.7 Quota 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.7.1 | `GET /api/quotas/me` | 查询当前用户额度 |
| 2.7.2 | 内部额度逻辑 | 创建任务时预扣、失败时退还、idempotency_key 唯一约束防重复 |

V1 额度规则：

- 创建视频任务时预扣 `videoQuota` 1 次，`idempotency_key = task:{taskId}:video:create`。
- AI 图片素材成功入库时扣 `imageQuota`，AI 视频片段成功入库时扣 `videoClipQuota`；同一素材重试成功不得重复扣。
- 任务在 `analyzing`、`plan_generated`、`script_generating`、`material_generating`、`rendering` 任一阶段最终失败且不可重试时，Java 按已成功扣减记录做幂等退款。
- `POST /api/videos/{videoId}/export` 成功生成下载 URL 时扣 `exportQuota`；重复导出同一 video 不重复扣。
- 所有扣减和退款必须在数据库事务内锁定 `user_quotas` 行，并先写 `quota_records` 幂等记录，再更新汇总计数。

### 2.8 Storage 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.8.1 | `POST /api/storage/presigned-upload-url` | 获取 COS 预签名上传 URL |

上传安全规则：

- Java 校验 `folder`、`mimeType`、`sizeBytes`、文件扩展名和用户归属；V1 商品图片最大 10MB，AI 素材/成片上传仅允许内部服务使用。
- COS object key 由服务端生成，格式包含 `userId/productId/taskId` 和随机 UUID，禁止客户端传入完整 object key。
- 预签名 URL 默认 10 分钟过期；上传完成后落库的 `fileUrl` 必须是 COS 允许的域名或内部 CDN 域名。
- Render Worker 下载素材时只允许访问 COS/CDN 白名单域名，禁止访问内网地址、metadata 地址和任意用户传入 URL。

### 2.9 Callback 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.9.1 | `POST /api/ai-callbacks/{taskId}` | Python AI 回调，校验内部 Token，更新任务状态和数据 |
| 2.9.2 | `POST /api/render-callbacks/{taskId}` | Render Worker 回调，校验内部 Token，创建 video 记录 |

### 2.10 Admin 模块

| # | 接口 | 说明 |
|---|---|---|
| 2.10.1 | `GET /api/admin/video-tasks` | 管理员查看全部任务 |
| 2.10.2 | `GET /api/admin/model-logs` | 模型调用日志 |
| 2.10.3 | `GET /api/admin/render-logs` | 渲染日志 |
| 2.10.4 | `GET /api/admin/users` | 管理员查看用户列表；若不补 OpenAPI，本页面不得进入 V1 |
| 2.10.5 | `GET /api/admin/quotas` / `PATCH /api/admin/quotas/{userId}` | 管理用户额度；若不补 OpenAPI，本页面不得进入 V1 |

### 2.11 任务状态机

严格遵守 `01-database-schema.sql` 中的状态转换：

```
draft → analyzing → analysis_completed → plan_generated → waiting_plan_selection
                                                                   ↓ (用户选方案)
                                                       script_generating → script_generated
                                                                                ↓
                                                                    material_generating → material_generated
                                                                                                ↓
                                                                                        rendering → checking → completed → exported
```

- 任何进行中状态均可转到 `failed`
- `failed` 可通过 retry 回到对应失败阶段
- 只允许 Java 后端修改任务状态，Worker 回调需校验当前状态是否合法

### 2.12 关键实现规则

- 所有用户面读写强制校验 `user_id` 归属
- V1 普通用户只允许创建 `pain_point_solution`、`before_after`、`review` 三种 `videoType`
- 使用 `videoType`（非 `videoStyle`），`template` 是渲染层字段
- AI 回调用 `schemaVersion + stage + status` 表达
- Render 回调 `status = failed` 时不要求 `videoId`
- 失败信息持久化到 `failed_stage`, `error_code`, `error_message`, `error_retryable`, `retry_count`
- 额度操作使用 idempotency_key 唯一约束
- 密码使用 BCrypt/Argon2id 哈希；refresh token 必须持久化、可撤销、支持轮换；禁用账号不得刷新 token
- 所有跨服务请求和队列消息携带 `taskId`、`correlationId`，AI 请求额外携带 `workflowId`，渲染请求额外携带 `renderTaskId`
- Callback handler 必须幂等：同一 `taskId + stage + status + schemaVersion` 或同一 `renderTaskId + status` 重复到达时返回成功但不重复写业务数据

### 2.13 阶段 2 验收标准

- `docker compose up` 后 Java 可连接 PostgreSQL，Flyway 完成 14 张 V1 表迁移。
- Auth/Product/VideoTask/Quota/Callback/Admin 核心接口通过 OpenAPI schema 校验。
- 使用 mock AI callback 可将任务从 `analyzing` 推进到 `waiting_plan_selection`。
- 并发创建任务测试证明 `videoQuota` 不会超扣，重复请求不会重复写 `quota_records`。

---

## 阶段 3：前端工作台——商品到方案选择

**目标**：用户可以注册登录、上传商品、创建任务、查看 AI 分析、选择视频方案。

### 3.1 全局框架

| # | 页面 | 路由 |
|---|---|---|
| 3.1.1 | 全局布局与导航 | 通用 |
| 3.1.2 | API 客户端层 (按 OpenAPI 生成 TypeScript 类型) | 通用 |
| 3.1.3 | JWT 认证流程 (登录态管理、Token 刷新) | 通用 |

### 3.2 用户端页面

| # | 页面 | 路由 | 说明 |
|---|---|---|---|
| 3.2.1 | 登录页 | `/login` | 邮箱 + 密码登录 |
| 3.2.2 | 注册页 | `/register` | 邮箱 + 密码注册 |
| 3.2.3 | 工作台首页 | `/dashboard` | 额度概览、最近任务、快捷入口 |
| 3.2.4 | 商品上传页 | `/products/new` | 表单 + 图片上传 (COS 预签名) + 创建视频任务 |
| 3.2.5 | 商品分析结果页 | `/products/:id/analysis` | 展示 AI 返回的卖点/痛点/受众/场景/风险提示 |
| 3.2.6 | 视频方案选择页 | `/video-tasks/:id/plans` | 3 个方案卡片，用户选择一个 |
| 3.2.7 | 任务进度组件 | 通用 | 基于 `video_tasks.status` 轮询的进度条 |

### 3.3 关键规则

- 前端**只**调 Java API
- `video_tasks.status` 以 Java 返回为准
- 不自行构造枚举值，使用 OpenAPI 定义的值
- V1 创建任务 UI 只展示 `pain_point_solution`、`before_after`、`review`
- 工作台风格：密集、清晰的操作型 UI
- 用户面错误信息简洁，`rawError` 仅管理员可见
- 阶段 3 开发前必须基于 `02-openapi-spec.yaml` 生成 TypeScript 类型，并使用 mock server/fixture 覆盖登录、商品上传、AI 分析、方案选择四条路径

### 3.4 阶段 3 验收标准

- 前端 API client 全部来自 OpenAPI 生成类型，无手写跨服务 DTO。
- 使用 mock server 可跑通注册登录、创建商品、创建任务、查看方案、选择方案。
- 权限错误、额度不足、AI 失败三类错误有用户可读提示，`rawError` 不出现在普通用户界面。

---

## 阶段 4：Python AI 编排服务

**目标**：Python 服务接收 Java 请求，执行完整的 AI 工作流，逐阶段回调 Java。

### 4.1 工作流定义

V1 使用两个 Temporal Workflow，避免长时间等待用户选择导致恢复和补偿复杂化：

```
ProductAnalysisWorkflow
1. analyze_product          → 商品分析
2. generate_video_plans     → 生成 3-5 个视频方案
3. callback_java            → 回调 Java，状态推进到 waiting_plan_selection

SelectedPlanGenerationWorkflow
1. generate_script          → 生成脚本
2. generate_storyboard      → 生成分镜
3. generate_asset_prompts   → 生成素材 Prompt
4. generate_image_assets    → 生成 AI 图片素材
5. generate_video_clips     → 生成 AI 视频片段
6. check_asset_quality      → 质量检查
7. build_render_manifest   → 组装 RenderManifest
8. callback_java            → 回调 Java，Java 投递渲染任务
```

用户选择方案后，由 Java 保存 `selectedPlanId`，再调用 Python 启动 `SelectedPlanGenerationWorkflow`。如后续改为单 Workflow + Temporal Signal，必须同步更新 OpenAPI 描述、状态恢复规则和重试策略。

### 4.2 Activity 详情

| Activity | 输入 | 输出 Schema | 说明 |
|---|---|---|---|
| `analyze_product` | ProductContext | `ProductAnalysis` | 分析类目、卖点、痛点、受众、场景、风险 |
| `generate_video_plans` | ProductAnalysis | `VideoPlanResult` | 3-5 个方案，每个含 hook/structure/reason/score |
| `generate_script` | SelectedPlan | 脚本 | 英文脚本、文案、标题 |
| `generate_storyboard` | Script | `StoryboardResult` | 4-12 个镜头，每个含 scene/action/subtitle/prompt/editInstruction |
| `generate_asset_prompts` | Storyboard | Prompt 列表 | 为每个镜头生成 AI 图片/视频 Prompt |
| `generate_image_assets` | Prompts | `MaterialResult` | 调用图片模型生成，上传 COS |
| `generate_video_clips` | Prompts | `MaterialResult` | 调用视频模型生成，上传 COS |
| `check_asset_quality` | Materials | `QualityCheckResult` | 检查 hook/产品出现/字幕可读/敏感词/画质 |
| `build_render_manifest` | Storyboard + Materials | `RenderManifest` | 组装完整渲染协议 |
| `callback_java` | 各阶段结果 | `AiCallbackPayload` | 携带 schemaVersion=1.0.0, stage, status |

### 4.3 API 接口

| # | 接口 | 说明 |
|---|---|---|
| 4.3.1 | `POST /ai/workflows/product-analysis` | Java 调用，启动商品分析与方案生成工作流 |
| 4.3.2 | `POST /ai/workflows/selected-plan-generation` | Java 调用，启动选方案后的脚本、分镜、素材和 RenderManifest 工作流 |
| 4.3.3 | `GET /ai/health` | 健康检查 |

### 4.4 JSON Schema + Pydantic 双重校验管道

```
1. 模型返回 raw text
2. 提取 JSON（剥离 Markdown/代码块/自然语言）
3. jsonschema 校验
4. Pydantic 转换内部对象
5. 校验失败 → 尝试修复
6. 修复失败 → 重试（最多 3 次）
7. 重试耗尽 → 回调 Java failed
```

### 4.5 AI 输出规则

- 必须是合法 JSON
- 禁止 Markdown、自然语言解释、代码块包裹
- 禁止额外字段
- 数组为空时返回 `[]`
- 分数范围 0-100
- 文案基于真实商品信息，不得编造虚假功效
- 合规风险字段必填
- 模型调用必须记录 provider、modelName、taskType、输入/输出摘要、token/cost 估算、耗时、status、errorMessage，并在 callback 或内部日志中携带 `correlationId`
- 单次任务的 AI 成本达到配置上限时停止后续生成，回调 Java failed，`errorCode = AI_COST_LIMIT_EXCEEDED`

### 4.6 V1 模型配置

| 用途 | 模型 |
|---|---|
| 商品分析 | 多模态 LLM (图片+文本理解) |
| 文案生成 | LLM (脚本/字幕/标题) |
| AI 图片生成 | 图片生成模型 |
| AI 视频生成 | 视频生成模型 |

### 4.7 RenderManifest 生成规则

按 `04-render-manifest-schema.md` 组装：

- `manifestVersion` = `"1.0.0"`
- `template` 根据 `videoType` 映射 (pain_point_solution → `pain_point_solution_v1`, etc.)
- `resolution` = `"1080x1920"`, `fps` = 30
- `assets[].type` 映射：`ai_image` → `image`, `ai_video` → `video`, `product_image` → `product_image`, `text_animation` → `text`
- 非 `text` 素材必须有 `url`
- `text` 素材必须有 `textContent`
- 字幕单条 ≤ 90 字符
- 素材总时长与 manifest `duration` 误差 ≤ 1 秒

### 4.8 阶段 4 验收标准

- 两个 Workflow 均可使用 fixture 输入在本地跑通，不依赖真实模型时可切换 fake provider。
- 所有 AI 输出先通过 JSON Schema 和 Pydantic 校验，再调用 Java callback。
- callback 重复发送不会导致 Java 重复插入 plans/storyboard/materials 或重复推进非法状态。
- 成本上限、模型超时、限流、JSON 修复失败均能回调标准 failed payload。

---

## 阶段 5：Node Render Worker——视频渲染

**目标**：消费 RabbitMQ 渲染任务，按 RenderManifest 渲染出 MP4，上传 COS，回调 Java。

### 5.1 RabbitMQ 消费

| 队列 | 用途 |
|---|---|
| `video.render.queue` | 渲染任务主队列 |
| `video.render.retry.queue` | 渲染重试队列 |
| `video.render.dlq` | 死信队列 |

消息体：

```json
{
  "taskId": "uuid",
  "renderTaskId": "uuid-or-stable-string",
  "correlationId": "uuid",
  "renderManifest": { ... },
  "callbackUrl": "https://api.example.com/api/render-callbacks/{taskId}"
}
```

`videoId` 由 Java 业务后端创建和持有。Render Worker 不创建、不查询、不理解 video 业务记录；成功回调只返回 `renderTaskId`、`videoUrl`、`coverUrl`、`duration`、`resolution`、`renderLog`，Java 在 callback handler 内幂等创建或更新 video 记录。
因此阶段 2 契约冻结前必须同步更新 `02-openapi-spec.yaml` 的 `RenderCallbackRequest`：`videoId` 不再是 Render Worker 成功回调的必需字段，可保留为兼容字段但 Java 不依赖它创建业务记录。

### 5.2 渲染流程

```
1. 消费 RabbitMQ 消息
2. 解析 renderManifest
3. JSON Schema 校验 (11 步校验流程)
4. 校验 template 存在
5. 校验 assets 数量 ≥ 4
6. 校验非 text 素材 URL 可访问
7. 校验 text 素材有 textContent
8. 校验总 duration 与 assets duration 总和一致 (误差 ≤ 1s)
9. 校验字幕长度 ≤ 90 字符
10. 校验音乐/配音/输出配置
11. 下载素材 (失败重试 3 次)
12. 选择 Remotion 模板并渲染
13. FFmpeg 转码 (MP4 / h264 / 指定码率)
14. 生成封面图
15. 上传 MP4 + 封面到 COS (失败重试 3 次)
16. 回调 Java (失败重试 3 次)
```

素材下载安全：

- 仅允许下载 COS/CDN 白名单域名下的 HTTPS URL。
- 禁止访问 localhost、内网网段、link-local、metadata 服务和非标准端口。
- 每个素材限制最大文件大小、下载超时和 MIME 类型；实际 MIME 与 manifest 不符时失败或降级。
- 下载后的临时文件按 `taskId/renderTaskId` 隔离，任务结束后清理。

### 5.3 V1 模板

| 模板 ID | 适用 videoType | 时间结构 |
|---|---|---|
| `pain_point_solution_v1` | pain_point_solution | 前 3s 痛点 → 3-7s 商品出现 → 7-14s 解决过程 → 14-19s 效果展示 → 19-22s 商品特写+引导 |
| `before_after_v1` | before_after | 前 2s 展示结果 → 2-6s 使用前 → 6-14s 使用过程 → 14-20s 前后对比 → 20-25s 商品特写 |
| `review_v1` | review | 前 3s 提出问题 → 3-8s 展示商品 → 8-18s 测试过程 → 18-23s 测试结果 → 23-30s 推荐理由 |

### 5.4 素材类型处理

| material type | 渲染行为 |
|---|---|
| `image` | 图片全屏铺满，按 edit.crop 处理 |
| `product_image` | 商品图居中展示，可加缩放动效 |
| `video` | 视频片段裁剪为 9:16 |
| `text` | 纯文字动画镜头，使用 textContent，不需要 url |

### 5.5 失败策略

| 失败点 | 处理方式 |
|---|---|
| JSON Schema 校验失败 | 直接回调 Java failed |
| 素材 URL 不可访问 | 重试下载 3 次 |
| 单个 AI 视频无法读取 | 尝试降级为对应封面帧或图片 |
| Remotion 渲染失败 | RabbitMQ 重试 |
| FFmpeg 转码失败 | RabbitMQ 重试 |
| COS 上传失败 | 重试 3 次 |
| Java 回调失败 | 重试 3 次，仍失败记录本地日志 |
| 队列消息重复消费 | 基于 `renderTaskId` 幂等跳过已完成任务，重复回调返回成功 |

### 5.6 回调规则

- 成功：携带 `renderTaskId`, `videoUrl`, `coverUrl`, `duration`, `resolution`, `renderLog`
- 失败：使用共享错误对象 (`errorCode`, `errorMessage`, `failedStage`, `retryable`)
- 失败时**不**要求 `videoUrl`, `coverUrl`
- 所有回调携带 `correlationId`；Java 根据 `taskId + renderTaskId + status` 做幂等处理

### 5.7 输出规格

| 项目 | 标准 |
|---|---|
| 格式 | MP4 |
| 分辨率 | 1080×1920 |
| 比例 | 9:16 |
| 帧率 | 30fps |
| 时长 | 15-30 秒 |
| 字幕 | 英文 |
| 音乐 | 内置可选 |

### 5.8 阶段 5 验收标准

- 使用固定 RenderManifest fixture 可本地渲染出 1080×1920 MP4 和封面图。
- template 不存在、素材 URL 不可访问、duration 不一致、字幕过长均能返回标准 failed callback。
- 重复投递同一 `renderTaskId` 不会重复上传成片或重复创建 video 记录。
- SSRF 测试覆盖 localhost、内网 IP、metadata 地址和非白名单域名。

---

## 阶段 6：前端收尾 + 管理后台 + 联调

**目标**：走通全流程，管理员可查看日志和成本，整体联调通过。

### 6.1 用户端收尾页面

| # | 页面 | 路由 | 说明 |
|---|---|---|---|
| 6.1.1 | 脚本分镜页 | `/video-tasks/:id/storyboard` | 展示 shots、允许编辑文本字段 |
| 6.1.2 | 生成进度页 | `/video-tasks/:id/progress` | 实时轮询展示 AI 阶段进度 |
| 6.1.3 | 成片预览页 | `/videos/:id` | 播放视频、标题/文案/标签、下载按钮 |
| 6.1.4 | 视频库 | `/videos` | 历史视频列表、筛选、搜索 |
| 6.1.5 | 额度页 | `/quota` | 各类额度使用情况 |
| 6.1.6 | 失败重试 UI | 通用 | 展示失败原因、提供重试按钮 |

### 6.2 管理后台页面

| # | 页面 | 路由 | 说明 |
|---|---|---|---|
| 6.2.1 | 管理台首页 | `/admin` | 系统概览 |
| 6.2.2 | 用户管理 | `/admin/users` | 查看用户列表；依赖 `GET /api/admin/users` 补入 OpenAPI |
| 6.2.3 | 任务管理 | `/admin/video-tasks` | 查看全部任务 |
| 6.2.4 | 视频管理 | `/admin/videos` | 查看全部成片；依赖 `GET /api/admin/videos` 补入 OpenAPI |
| 6.2.5 | 模型日志 | `/admin/model-logs` | AI 调用成本和状态 |
| 6.2.6 | 渲染日志 | `/admin/render-logs` | 渲染成功/失败记录 |
| 6.2.7 | 额度管理 | `/admin/quotas` | 管理用户额度；依赖 admin quota API 补入 OpenAPI |

不在 `02-openapi-spec.yaml` 中定义的管理页面不得进入 V1 可交付范围；可以保留导航占位，但必须隐藏入口。

### 6.3 集成测试

| # | 测试场景 | 覆盖路径 |
|---|---|---|
| 6.3.1 | 正向全流程 | 上传 → 分析 → 选方案 → 脚本 → 素材 → 渲染 → 下载 |
| 6.3.2 | 额度不足 | 额度为 0 时创建任务被拒绝 |
| 6.3.3 | AI 失败 | Python 回调 failed → 任务状态 failed → 用户可见错误 → 重试 |
| 6.3.4 | 素材生成失败 | 单个镜头素材失败 → 质量检查标记 → 降级处理 |
| 6.3.5 | 渲染失败 | Render Worker 失败 → 死信队列 → 任务 failed → 重试 |
| 6.3.6 | 权限隔离 | 用户 A 不能访问用户 B 的 task/video |
| 6.3.7 | 并发额度 | 同时创建多个任务不超扣额度 |
| 6.3.8 | 重复回调 | AI/Render callback 重复到达 → 不重复写数据、不重复扣退额度 |
| 6.3.9 | 队列重复消费 | 同一 renderTaskId 重复投递 → 只产生一个最终视频 |
| 6.3.10 | 成本上限 | AI 成本超过配置 → 任务 failed，错误码可见，额度按规则退还 |
| 6.3.11 | 上传与下载安全 | 非法 MIME、超大文件、非白名单 URL、内网 URL 均被拒绝 |
| 6.3.12 | 契约一致性 | DB/OpenAPI/AI Schema/RenderManifest fixture 全部通过自动校验 |

### 6.4 阶段 6 验收标准

- 端到端正向流程可从商品上传跑到 MP4 预览和导出。
- 所有 V1 页面使用真实 Java API 或受控 mock，不直连 Python/Render。
- 管理后台只展示 OpenAPI 已定义且后端已实现的页面。
- 全链路日志可用 `taskId`、`correlationId`、`workflowId`、`renderTaskId` 追踪一次任务。

---

## 依赖关系总览

```
阶段 1 ──── 基础设施 (所有服务可启动)
  │
  ▼
阶段 2 ──── Java 后端 14 张表 + Auth/Product/Task/Quota/Callback/Admin
  │
  ├──────────┐
  ▼          ▼
阶段 3      阶段 4 ──── Python AI 编排 (Workflow + Activity + 校验管道)
前端前半段      │
(上传→分析    ▼
→选方案)   阶段 5 ──── Node Render Worker (RabbitMQ + Remotion + FFmpeg)
  │          │
  └────┬─────┘
       ▼
阶段 6 ──── 前端收尾 + 管理后台 + 端到端联调
```

## 契约冻结里程碑

阶段 2 完成后、阶段 3/4 并行前，必须冻结以下内容：

| 契约项 | 冻结标准 |
|---|---|
| V1 videoType | 仅 `pain_point_solution`、`before_after`、`review` 对普通用户开放 |
| 状态机 | DB、OpenAPI、Java 状态转换表完全一致 |
| AI callback | `schemaVersion + stage + status + nextTaskStatus + error` fixture 通过校验 |
| Render callback | 成功/失败 payload fixture 通过 OpenAPI 校验，失败不要求成功字段 |
| RenderManifest | 3 个 V1 template fixture 通过 JSON Schema 校验 |
| 队列消息 | `taskId + renderTaskId + correlationId + renderManifest + callbackUrl` 固定 |
| 额度 | consume/refund/grant 的 idempotency key 规则固定 |
| Admin API | 进入 V1 的管理页面必须先进入 OpenAPI |

冻结后如需改字段、枚举、状态、payload，必须同时更新 4 份契约文档和受影响服务。

---

## 风险矩阵

| 风险 | 影响等级 | 发生概率 | 应对措施 |
|---|---|---|---|
| AI 模型输出不可控 JSON | 高 | 高 | 阶段 4.4 双重校验 + 修复重试管道，最多 3 次修复 |
| 跨服务字段不一致 | 高 | 中 | 每个阶段前阅读对应契约文件，字段严格对齐 4 份 docs |
| V1 videoType 与模板不一致 | 高 | 中 | V1 只开放 3 种 videoType；新增类型必须补模板、映射和 E2E 测试 |
| Render `videoId` 所属权不清 | 高 | 中 | Java 持有 video 记录；Render Worker 只返回渲染产物和 renderTaskId |
| 用户选方案等待导致工作流恢复复杂 | 中 | 中 | V1 拆成两个 Temporal Workflow，选方案后由 Java 启动第二段 |
| 阶段 3/4 伪并行造成返工 | 中 | 中 | 阶段 2 增加契约冻结、mock server 和 fixture 后再并行 |
| 视频渲染耗时长 | 中 | 高 | 异步队列 + 前端轮询 + 进度条展示 |
| COS 上传不稳定 | 中 | 中 | 预签名 URL 时效控制 + 上传失败重试 3 次 |
| 额度并发超扣 | 高 | 低 | idempotency_key 唯一约束 + 数据库行锁 |
| 素材额度、导出额度重复扣退 | 高 | 中 | 所有 quota_records 使用稳定 idempotency key，重试和重复导出不重复扣 |
| AI 视频生成质量不稳定 | 中 | 高 | 产品图动效降级方案 + 多素材备选 + 质量检查 |
| Python Temporal 学习曲线 | 中 | 中 | V1 可用简单 HTTP 调用链替代 Temporal，后续迁移 |
| Admin 页面与 OpenAPI 不一致 | 中 | 中 | 管理页面必须先进入 OpenAPI；未定义接口隐藏页面入口 |
| 上传/素材下载安全风险 | 高 | 中 | 服务端生成 COS key，限制 MIME/大小/域名，Render Worker 做 SSRF 白名单 |
| 异步链路无法排查 | 高 | 中 | 全链路携带 `taskId`、`correlationId`、`workflowId`、`renderTaskId` |
| 模型成本失控或第三方限流 | 高 | 中 | 设置单任务成本上限、超时和限流错误码，失败后按额度规则补偿 |
| 队列重复消费或回调重复到达 | 高 | 中 | Callback 和 renderTask 均按稳定业务键幂等处理 |
| 素材版权/合规风险 | 中 | 中 | AI 输出合规字段必填，质量检查记录风险分，风险过高时阻断渲染或提示用户 |

---

## V1 不实现的功能（明确排除）

| 功能 | 推迟原因 |
|---|---|
| 自动发布到 TikTok | 账号风控和平台 API 对接复杂 |
| 多语言支持 | V1 只做英文 |
| 多账号管理 | 后续版本 |
| 素材库管理 | 后续版本 |
| 商品选品 | 无数据源，V1 先不做 |
| 竞品视频分析 | V2 功能 |
| 批量生成 | V2 功能 |
| 数据看板/GMV 跟踪 | 后续版本 |
| 时间轴拖拽编辑 | V1 只支持文本表单编辑 |

---

## V2+ 版本展望

| 版本 | 功能 |
|---|---|
| V2 | 一品多视频、批量生成脚本/分镜、竞品视频分析、多封面测试 |
| V3 | 商品选品、数据复盘、商品评分、视频数据录入 |
| V4 | 多账号矩阵运营、店铺管理、TikTok 账号管理、定时发布 |
| V5 | 自动化系统：高表现视频结构自动复制、素材智能推荐、商品自动启停 |

---

## 附录：契约文件速查

| 文件 | 管辖范围 | 谁来读 |
|---|---|---|
| `01-database-schema.sql` | 14 张表、状态机、枚举值、额度幂等 | Java 后端、Python (仅参考) |
| `02-openapi-spec.yaml` | REST API 边界、回调契约、共享 ErrorDetail | 全部服务 |
| `03-ai-output-json-schema.md` | AI 6 个输出 Schema、Pydantic 校验管道 | Python AI、Java Callback 模块 |
| `04-render-manifest-schema.md` | Python→Render Worker 渲染协议、模板映射 | Python AI、Render Worker |

## 附录：开发 Skill 速查

| Skill | 对应服务 | 触发条件 |
|---|---|---|
| `tk-video-contracts` | 跨服务总控 | 任何跨服务接口变更 |
| `tk-video-backend` | Java Spring Boot | 后端开发 |
| `tk-video-ai-orchestrator` | Python AI 编排 | AI 服务开发 |
| `tk-video-render-worker` | Node Render Worker | 渲染服务开发 |
| `tk-video-frontend-workbench` | Next.js 前端 | 前端开发 |

---

> 文档结束。V1 总计 **6 阶段、约 50 个任务**，覆盖 5 个服务。
> 执行顺序严格遵守依赖关系，阶段 3 和阶段 4 可并行推进。
