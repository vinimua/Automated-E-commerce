# TikTok Shop AI 带货视频生成系统 — 阶段 2 实现文档

> 版本：1.0  
> 日期：2026-06-11  
> 状态：阶段 2 已完成（108 个 Java 源文件，编译通过）

---

## 1. 项目概览

### 1.1 系统目标

构建 AI 驱动的 TikTok 带货视频自动生成系统。V1 目标：打通一条完整闭环 ——

```
商品上传 → AI 分析 → 生成方案 → 用户选方案 → 脚本分镜 → AI 素材 → 自动渲染 → 导出 MP4
```

### 1.2 技术栈

| 服务 | 技术 | 端口 | 职责 |
|---|---|---|---|
| **Frontend (web)** | Next.js 14, TypeScript, Tailwind CSS, shadcn/ui | 3000 | 用户界面 |
| **Java API (api-java)** | Spring Boot 3.3, Java 21, MyBatis-Plus 3.5, JJWT 0.12, Flyway | 8080 | 业务逻辑、唯一状态源 |
| **Python AI (ai-orchestrator)** | FastAPI, Pydantic 2, Temporal SDK | 8000 | AI 编排、JSON Schema 校验 |
| **Render Worker** | Node.js, Remotion 4, FFmpeg, RabbitMQ (amqplib) | 后台 | 视频渲染 |
| **PostgreSQL 16** | — | 15432 | 主数据库（14 张表） |
| **Redis 7** | — | 16380 | 缓存/临时状态 |
| **RabbitMQ 3.13** | — | 15673/25673 | 渲染任务队列 |
| **Temporal** | — | 17233/18088 | AI 工作流编排 |

### 1.3 服务拓扑

```
┌──────────────┐     HTTP JWT      ┌──────────────────┐
│  Next.js Web  │ ───────────────→ │  Java Spring Boot │
│  (Frontend)   │                  │  (API + State)    │
└──────────────┘                  └──┬────────────┬───┘
                                     │ callback   │ callback
                              ┌──────▼──┐   ┌─────▼────────┐
                              │ Python  │   │ Node Render   │
                              │ FastAPI │   │ Worker        │
                              │+Temporal│   │+Remotion+FFmpeg│
                              └─────────┘   └──────────────┘
                                     │            │
                                     └─────┬──────┘
                                           │
                              ┌────────────▼────────────┐
                              │  PostgreSQL (唯一状态源)  │
                              └─────────────────────────┘
```

### 1.4 关键不变量

- 前端只调 Java API，不直连 Python / Render Worker
- Java + PostgreSQL 是唯一主状态源
- Python AI 通过回调通知 Java，不直写业务表
- Render Worker 只理解 RenderManifest，不理解用户/商品/任务
- 跨服务请求携带 `taskId` + `correlationId`
- 回调处理器必须幂等
- 额度操作使用 `idempotency_key` UNIQUE 约束

---

## 2. 数据模型字典

> 完整的 14 张表 DDL 见 `docs/01-database-schema.sql`

### 2.1 核心实体（Java Entity 类）

所有实体使用 MyBatis-Plus 注解，UUID 主键（`gen_random_uuid()`），`OffsetDateTime` 时间戳，JSONB 字段使用 `JacksonTypeHandler`。

#### users → UserEntity

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | UUID | PK, auto | 用户唯一标识 |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 登录邮箱 |
| password_hash | VARCHAR(255) | NOT NULL | Argon2id 哈希 |
| role | VARCHAR(50) | NOT NULL, DEFAULT 'user' | user / admin |
| status | VARCHAR(50) | NOT NULL, DEFAULT 'active' | active / disabled |
| created_at | TIMESTAMPTZ | NOT NULL | 注册时间 |
| updated_at | TIMESTAMPTZ | NOT NULL | 最后更新时间 |

#### video_tasks → VideoTaskEntity

| 字段 | 类型 | 说明 |
|---|---|---|
| id | UUID PK | 任务唯一标识 |
| user_id | UUID FK | 所属用户 |
| product_id | UUID FK | 关联商品 |
| status | VARCHAR(80) | 状态机：draft → … → completed → exported |
| progress | INT (0-100) | 进度百分比 |
| duration | INT (15/20/25/30) | 视频时长（秒） |
| video_type | VARCHAR(100) | V1 仅 pain_point_solution / before_after / review |
| need_subtitles | BOOLEAN | 默认 true |
| need_voiceover | BOOLEAN | 默认 false |
| selected_plan_id | UUID FK | 用户选中的方案 |
| render_manifest | JSONB | Python AI 生成的渲染协议 |
| manifest_version | VARCHAR(50) | 固定 "1.0.0" |
| schema_version | VARCHAR(50) | 固定 "1.0.0" |
| failed_stage | VARCHAR(100) | 失败阶段（product_analysis / storyboard / …） |
| error_code | VARCHAR(100) | 错误码 |
| error_message | TEXT | 用户可见错误信息 |
| error_retryable | BOOLEAN | 是否可重试 |
| retry_count | INT | 已重试次数 |
| ai_workflow_id | VARCHAR(255) | Temporal 工作流 ID |
| render_task_id | VARCHAR(255) | 渲染任务 ID |

#### user_quotas + quota_records

**user_quotas** — 用户额度汇总（一个用户一行）：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| video_quota | INT | 10 | 成片生成总次数 |
| image_quota | INT | 50 | AI 图片生成总次数 |
| video_clip_quota | INT | 10 | AI 视频片段总次数 |
| export_quota | INT | 10 | 导出下载总次数 |
| used_*_count | INT | 0 | 已使用次数 |

**quota_records** — 额度流水账（不可变，幂等键唯一）：

| 字段 | 类型 | 说明 |
|---|---|---|
| idempotency_key | VARCHAR(255) | UNIQUE — 防重复扣退 |
| type | VARCHAR(100) | video / image / video_clip / export |
| amount | INT (>0) | 本次操作数量 |
| direction | VARCHAR(50) | consume / refund / grant |

**幂等键命名规范：**

| 操作 | 幂等键格式 |
|---|---|
| 创建任务预扣 | `task:{taskId}:video:create` |
| 失败退款 | `task:{taskId}:refund:video:{retryCount}` |
| 导出扣费 | `video:{videoId}:export` |

#### products → ProductEntity

| 字段 | 类型 | 说明 |
|---|---|---|
| name | VARCHAR(255) NOT NULL | 商品名称 |
| description | TEXT | 商品描述 |
| target_market | VARCHAR(50) | 目标市场（如 US） |
| language | VARCHAR(50) | 语言（如 en） |
| selling_points | JSONB (List\<String\>) | AI 分析 - 卖点 |
| pain_points | JSONB (List\<String\>) | AI 分析 - 痛点 |
| target_audience | JSONB (List\<String\>) | AI 分析 - 目标受众 |
| scenes | JSONB (List\<String\>) | AI 分析 - 使用场景 |
| video_score | INT (0-100) | AI 评分 |
| risk_tips | JSONB (List\<String\>) | 合规风险提示 |

#### storyboard_shots → StoryboardShotEntity

| 字段 | 类型 | 说明 |
|---|---|---|
| shot_no | INT NOT NULL | 镜头序号（1-based） |
| duration | INT (>0) | 镜头时长（1-8 秒） |
| scene | TEXT NOT NULL | 画面描述 |
| subtitle | TEXT NOT NULL | 字幕（≤90 字符） |
| material_type | VARCHAR(100) | product_image / ai_image / ai_video / text_animation / … |
| prompt | TEXT | AI 生成 Prompt |
| edit_instruction | TEXT | 编辑指令 |

### 2.2 DTO 请求/响应结构

所有 API 响应包裹在 `ApiResponse<T>` 中：

```json
{
  "code": 0,
  "message": "success",
  "data": { … }
}
```

分页列表包裹在 `PageResult<T>` 中：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [ … ],
    "page": 1,
    "pageSize": 20,
    "total": 105,
    "totalPages": 6
  }
}
```

详细 DTO 字段见附录 A。

### 2.3 AI 回调载荷（AiCallbackRequest）

```json
{
  "taskId": "uuid",
  "schemaVersion": "1.0.0",
  "stage": "product_analysis | video_plan | storyboard | material | quality_check | render_manifest",
  "status": "success | failed",
  "nextTaskStatus": "analysis_completed | plan_generated | script_generated | …",
  "productAnalysis": { "category": "…", "sellingPoints": […], … },
  "plans": [{ "type": "pain_point_solution", "title": "…", "hook": "…", "score": 85 }],
  "storyboard": { "title": "…", "hook": "…", "shots": [{ "shotNo": 1, … }] },
  "materials": [{ "shotNo": 1, "type": "ai_image", "url": "…" }],
  "renderManifest": { "manifestVersion": "1.0.0", "template": "pain_point_solution_v1", … },
  "error": {
    "errorCode": "AI_COST_LIMIT_EXCEEDED",
    "errorMessage": "…",
    "failedStage": "material",
    "retryable": true
  }
}
```

### 2.4 渲染回调载荷（RenderCallbackRequest）

```json
// 成功
{
  "taskId": "uuid",
  "renderTaskId": "string",
  "status": "completed",
  "videoUrl": "https://…/output.mp4",
  "coverUrl": "https://…/cover.jpg",
  "duration": 22,
  "resolution": "1080x1920",
  "renderLog": { "template": "pain_point_solution_v1", "renderTimeMs": 45000 }
}

// 失败
{
  "taskId": "uuid",
  "status": "failed",
  "error": { "errorCode": "RENDER_FAILED", "errorMessage": "…", "failedStage": "rendering", "retryable": true }
}
```

---

## 3. API 接口说明

### 3.1 Auth 模块

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 无 | 注册（email + password ≥ 8 字符），返回 JWT + refresh token，自动创建初始额度 |
| POST | `/api/auth/login` | 无 | 登录，验证密码（Argon2id），返回 token 对 |
| POST | `/api/auth/refresh` | 无 | 刷新 token（SHA-256 哈希查找 → 轮换 → 发放新对） |

**注册示例：**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test12345"}'

# Response:
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "a1b2c3d4-…",
    "accessToken": "eyJhbGciOi…",
    "refreshToken": "eyJhbGciOi…"
  }
}
```

### 3.2 Product 模块

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/products` | JWT | 创建商品 + 图片（第一张为主图） |
| GET | `/api/products` | JWT | 分页列表（用户隔离，排除 deleted） |
| GET | `/api/products/{id}` | JWT | 详情（含所有权校验） |
| PATCH | `/api/products/{id}` | JWT | 更新卖点/痛点/受众/场景 |

### 3.3 VideoTask 模块

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/video-tasks` | JWT | 创建任务（V1 类型校验 + 预扣额度 + 调度 AI） |
| GET | `/api/video-tasks` | JWT | 分页列表（支持 status / productId 筛选） |
| GET | `/api/video-tasks/{id}` | JWT | 详情 |
| POST | `/api/video-tasks/{id}/select-plan` | JWT | 选择方案（状态机校验 + 调度 AI Phase 2） |
| POST | `/api/video-tasks/{id}/retry` | JWT | 重试失败任务（退款 + 重置状态） |

**创建任务示例：**
```bash
curl -X POST http://localhost:8080/api/video-tasks \
  -H "Authorization: Bearer eyJhbGciOi…" \
  -H "Content-Type: application/json" \
  -d '{"productId":"uuid","duration":20,"videoType":"pain_point_solution","needSubtitles":true}'

# Response:
{ "code": 0, "message": "success", "data": { "taskId": "uuid", "status": "analyzing", "progress": 0 } }
```

### 3.4 Storyboard 模块

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/video-tasks/{id}/plans` | JWT | 获取 AI 生成的视频方案列表 |
| GET | `/api/video-tasks/{id}/storyboard` | JWT | 获取分镜脚本（含 shots） |
| PATCH | `/api/storyboards/{id}` | JWT | 编辑分镜文本（title/hook/caption/hashtags/shots） |

### 3.5 Video 模块

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/videos` | JWT | 视频列表（支持 status/productId 筛选） |
| GET | `/api/videos/{id}` | JWT | 视频详情 |
| POST | `/api/videos/{id}/export` | JWT | 导出（幂等扣 exportQuota，状态 → exported） |

### 3.6 Storage 模块

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/storage/presigned-upload-url` | JWT | 获取 COS 预签名上传 URL |

**校验规则：**
- `folder` 仅允许：product-images / ai-images / ai-clips / final-videos / covers
- `mimeType` 仅允许：JPEG, PNG, WebP, GIF, MP4, QuickTime, ZIP
- `sizeBytes` 上限 10MB
- COS key 格式：`tk-ai-video/{folder}/{userId}/{uuid}/{sanitizedFileName}`

### 3.7 Callback 模块（内部服务）

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/ai-callbacks/{taskId}` | X-Internal-Service-Token | AI 阶段回调 |
| POST | `/api/render-callbacks/{taskId}` | X-Internal-Service-Token | 渲染结果回调 |

### 3.8 Admin 模块（需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/video-tasks` | 查看全部任务 |
| GET | `/api/admin/model-logs` | 模型调用日志 |
| GET | `/api/admin/render-logs` | 渲染日志 |

### 3.9 错误码速查

| HTTP 状态 | code | 说明 |
|---|---|---|
| 200 | 0 | 成功 |
| 400 | 40000 | 业务逻辑错误 |
| 400 | 40001 | 参数校验失败 |
| 401 | 40100 | 认证失败 |
| 403 | 40300 | 资源不属于当前用户 |
| 403 | 40301 | 权限不足（需 ADMIN） |
| 404 | 40400 | 资源不存在 |
| 409 | 40910 | 额度不足 |
| 409 | 40920 | 非法状态转换 |
| 500 | 50000 | 服务器内部错误 |

---

## 4. 消息队列协议

### 4.1 队列拓扑

| 队列 | 用途 |
|---|---|
| `video.render.queue` | 渲染任务主队列 |
| `video.render.retry.queue` | 重试队列（TTL 60s → 自动回主队列） |
| `video.render.dlq` | 死信队列（重试耗尽后进入） |

### 4.2 消息结构

```json
{
  "taskId": "uuid",
  "renderTaskId": "uuid-or-stable-string",
  "correlationId": "uuid",
  "renderManifest": {
    "manifestVersion": "1.0.0",
    "template": "pain_point_solution_v1",
    "resolution": "1080x1920",
    "fps": 30,
    "duration": 22,
    "assets": [ … ]
  },
  "callbackUrl": "https://api.example.com/api/render-callbacks/{taskId}"
}
```

### 4.3 消费流程

```
1. 消费 RabbitMQ 消息 (prefetch = 1, sequential)
2. JSON 解析消息
3. 调用 11 步 Manifest 校验
   ├── 校验失败 → callback Java (failed, retryable=false) → ack
   └── 校验通过 → 继续
4. Remotion 渲染 → FFmpeg 转码 MP4 h264
5. 腾讯云 COS 上传（失败重试 3 次）
6. callback Java
   ├── 成功 → ack
   └── 失败 → 本地日志记录
```

### 4.4 重试策略

| 失败点 | 处理方式 |
|---|---|
| JSON Schema 校验失败 | 直接回调 failed，不重试 |
| 素材 URL 不可访问 | 重试下载 3 次 |
| Remotion 渲染失败 | channel.reject(msg, true) → RabbitMQ 重试 |
| COS 上传失败 | 重试 3 次 |
| Java 回调失败 | 重试 3 次（间隔 2s/4s/6s） |
| 重复消息 | 基于 renderTaskId 幂等跳过 |

---

## 5. 核心流程详解

### 5.1 完整生命周期

```
用户注册 → 创建商品 → 创建视频任务
                           │
                           ▼
              ┌─────────────────────────┐
              │  ProductAnalysisWorkflow │
              │  1. analyze_product      │
              │  2. generate_video_plans │
              │  3. callback_java        │
              └───────────┬─────────────┘
                          │ (status → plan_generated → waiting_plan_selection)
                          ▼
                   用户选择一个方案
                          │
                          ▼
              ┌──────────────────────────────────┐
              │  SelectedPlanGenerationWorkflow   │
              │  1. generate_script               │
              │  2. generate_storyboard           │
              │  3. generate_asset_prompts        │
              │  4. generate_image_assets         │
              │  5. generate_video_clips          │
              │  6. check_asset_quality           │
              │  7. build_render_manifest         │
              │  8. callback_java                 │
              └───────────┬──────────────────────┘
                          │ (status → material_generated → rendering)
                          ▼
              ┌─────────────────────────┐
              │  Node Render Worker      │
              │  1. 11 步 Manifest 校验  │
              │  2. Remotion 渲染        │
              │  3. FFmpeg 转码          │
              │  4. COS 上传             │
              │  5. callback Java "completed" │
              └───────────┬─────────────┘
                          │ (status → completed)
                          ▼
                    用户预览视频 → 导出下载
```

### 5.2 任务状态机

```
draft → analyzing → analysis_completed → plan_generated
                                               ↓
                                     waiting_plan_selection (等待用户选择)
                                               ↓ (用户选方案)
                                     script_generating → script_generated
                                                              ↓
                                                  material_generating → material_generated
                                                                              ↓
                                                                      rendering → checking → completed → exported

任意进行中状态 → failed → retry → 回到对应失败阶段
```

**Retry 映射表：**

| failedStage | 重试目标 |
|---|---|
| product_analysis | analyzing |
| video_plan | analyzing |
| storyboard | script_generating |
| material | material_generating |
| quality_check | material_generating |
| render_manifest | rendering |

### 5.3 额度生命周期

```
注册 → 初始额度 (video:10, image:50, clip:10, export:10)
  │
  ├── 创建任务 → consume(video, 1, key="task:{id}:video:create")
  │     │
  │     ├── 任务成功 → 不做额外操作
  │     └── 任务失败 → refund(video, 1, key="task:{id}:refund:video:{retry}")
  │
  ├── AI 素材入库 → consume(image/clip, 1, key="task:{id}:image:{materialId}")
  └── 导出视频 → consume(export, 1, key="video:{id}:export")
```

---

## 6. 模块设计思路

### 6.1 为什么用 MyBatis-Plus 而不是 JPA/Hibernate？

- **MyBatis-Plus** 对 PostgreSQL 原生 SQL 的支持更直接，`SELECT … FOR UPDATE`、JSONB 操作、`ON DELETE CASCADE` 等不需要 ORM 映射层
- `BaseMapper<T>` 自动生成 CRUD，减少样板代码
- `LambdaQueryWrapper` 类型安全的查询条件，避免字符串拼 SQL
- XML Mapper 仅用于 `FOR UPDATE` 和复杂连接查询

### 6.2 为什么状态机是静态 Map 而不是数据库约束？

- 状态转换规则是**业务逻辑**，不是数据完整性约束
- 放在 Java 代码中更易于测试、更易于在 V2 扩展
- `VideoTaskStateMachine.validateTransition()` 在任何状态变更前被调用，抛出 `InvalidStateTransitionException`
- DB 层面用 CHECK 约束确保只有 14 种合法状态值，但不限制转换

### 6.3 为什么额度操作要先 INSERT 再 UPDATE？

```java
// Step 1: INSERT quota_records (UNIQUE idempotency_key)
try { quotaRecordMapper.insert(record); }
catch (DuplicateKeyException e) { return; /* 幂等，跳过 */ }

// Step 2: FOR UPDATE user_quotas → 更新计数器
UserQuotaEntity quota = userQuotaMapper.selectByUserIdForUpdate(userId);
```

- **INSERT first** 利用数据库 UNIQUE 约束实现幂等：即使并发重复请求，只有一个能插入成功
- **FOR UPDATE** 悲观锁确保计数器不会被并发覆盖
- 如果在 UPDATE 之后事务回滚，INSERT 也被回滚，下次请求会重新执行

### 6.4 为什么 AI 调用是 fire-and-forget？

- Java → Python AI 的 HTTP 调用**不等待结果**
- 如果 Python 不可达，任务停留在 `analyzing`，前端轮询显示"处理中"
- V2 会加 `@Scheduled` 定时任务：超过 30 分钟仍在 `analyzing` 的任务自动标记 `failed`
- 这避免了同步 HTTP 调用的超时问题和重试复杂性

### 6.5 为什么回调要幂等？

- Python AI 和 Render Worker 的网络可能不稳定，会重试
- 回调幂等检查：如果任务状态已经反映了回调结果（如已是 `plan_generated`），返回 200 但不重复写数据
- Render 回调幂等：检查同一个 `taskId + renderTaskId` 是否已创建 video 记录

### 6.6 为什么 COS 上传 URL 是服务端生成 key？

- 客户端不可信：不能由客户端传入完整 object key
- 格式强制：`tk-ai-video/{folder}/{userId}/{uuid}/{sanitizedFileName}`
- 安全校验：folder、mimeType、sizeBytes 三重验证
- 文件名 sanitize：移除特殊字符，防止路径穿越

### 6.7 为什么 Render Worker 要做 11 步校验？

- RenderManifest 从 Python 生成到实际渲染之间有网络传输
- 前置校验可以**快速失败**，而不是等 Remotion 渲染到一半才发现素材缺失
- 11 步覆盖：版本号 → 模板存在 → assets 数量 → URL 可访问 → text 字段 → 时长匹配 → 字幕长度 → 分辨率 → 帧率 → URL 安全 → 文件大小
- 校验失败直接回调 failed（不重试），节省计算资源

---

## 7. 开发阶段说明

### 阶段 1 ✅ 完成
- Monorepo 目录结构
- Docker Compose 基础设施（PostgreSQL/Redis/RabbitMQ/Temporal）→ 已部署腾讯云 `124.223.200.16`
- 4 个服务脚手架（Spring Boot / Next.js / FastAPI / Render Worker）
- Flyway 数据库迁移（14 张 V1 表）
- 环境变量配置（.env, CLAUDE.md）

### 阶段 2 ✅ 完成（本文档）
- **108 个 Java 源文件，编译通过**
- 11 个业务模块：Auth、User、Product、VideoTask、Storyboard、Video、Quota、Storage、Callback、Admin、Log
- 完整的状态机实现
- JWT 认证（access + refresh token 轮换）
- 幂等额度管理
- COS 预签名 URL 生成
- AI/Render 回调处理器

### 阶段 3（前端前半段）🟡 待开发
- 登录/注册页面
- 商品上传页（COS 预签名上传）
- AI 分析结果展示页
- 视频方案选择页
- API Client 层（OpenAPI 生成 TypeScript 类型）

### 阶段 4（Python AI 编排）🟡 待开发
- Temporal 工作流实现
- 10 个 Activity 实现
- Pydantic + JSON Schema 双重校验管道
- Fake provider 用于测试

### 阶段 5（Node Render Worker）🟡 待开发
- Remotion 3 个 V1 模板完整实现
- 11 步校验管道完整实现
- COS SDK 集成（cos-nodejs-sdk-v5）
- FFmpeg 转码流水线

### 阶段 6（前端收尾 + 联调）🟡 待开发
- 分镜编辑页、生成进度页、成片预览页
- 视频库、额度页
- 管理后台（需先补 OpenAPI）
- 端到端集成测试

### 当前 TODO / 占位实现

| 位置 | 说明 |
|---|---|
| `StorageServiceImpl` | 预签名 URL 是拼接字符串，Phase 5 集成 `cos_api` SDK 生成真实签名 |
| `AiServiceClient` | HTTP 调用是真实发出的，但 Python 端返回 stub 响应 |
| `python workflows.py` | 两个端点返回 "will be started (Phase 4)" |
| `render-consumer.ts` | 消费者框架就绪，实际渲染逻辑待 Phase 5 |
| `cos-uploader.ts` | 返回 placeholder URL |
| `AdminController` | admin users/quotas 管理接口未实现（待补 OpenAPI） |
| `build.gradle` | toolchain 本地设为 Java 17，服务器部署时改回 Java 21 |

---

## 8. 本地运行指南

### 8.1 前置依赖

| 工具 | 版本要求 |
|---|---|
| Java JDK | 21（本地开发可用 17+） |
| Node.js | 20+ |
| Python | 3.12+ |
| Docker Desktop | 最新版（用于启动中间件，或连接腾讯云服务器） |
| Gradle | 8.10（通过 `./gradlew` wrapper，无需全局安装） |

### 8.2 环境变量

```bash
# 复制模板
cp .env.example .env

# 编辑 .env，填入以下必填项：
COS_SECRET_ID=AKID…          # 腾讯云 SecretId
COS_SECRET_KEY=…             # 腾讯云 SecretKey
COS_REGION=ap-guangzhou
COS_BUCKET=automatedecommerce-1314706054

# 可选
OPENAI_API_KEY=              # Phase 4 才需要
ANTHROPIC_API_KEY=           # Phase 4 才需要
```

### 8.3 启动基础设施（腾讯云服务器上已运行，本地开发可跳过）

```bash
cd infra
docker compose up -d
```

验证：
```bash
curl http://124.223.200.16:15432   # PostgreSQL
curl http://124.223.200.16:16380   # Redis (PONG)
curl http://124.223.200.16:25673   # RabbitMQ UI
curl http://124.223.200.16:18088   # Temporal UI
```

### 8.4 启动 Java API

```bash
cd apps/api-java

# 首次运行：Flyway 自动执行数据库迁移（14 张表）
./gradlew bootRun

# 验证
curl http://localhost:8080/actuator/health
```

### 8.5 启动 Python AI（Phase 4 需要）

```bash
cd services/ai-orchestrator
uv sync
uv run uvicorn src.main:app --reload --port 8000

# 验证
curl http://localhost:8000/ai/health
```

### 8.6 启动 Render Worker（Phase 5 需要）

```bash
cd apps/render-worker
npm install
npm run dev

# 日志显示 "[render-consumer] Listening on queue: video.render.queue"
```

### 8.7 模拟端到端测试

```bash
# 1. 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@test.com","password":"Demo12345"}'
# → 保存返回的 accessToken

TOKEN="eyJhbGciOi…"

# 2. 创建商品
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Earbuds Pro",
    "description": "Noise-cancelling Bluetooth earbuds",
    "imageUrls": ["https://example.com/earbuds1.jpg"],
    "targetMarket": "US",
    "language": "en"
  }'
# → 保存返回的 productId

PRODUCT_ID="…"

# 3. 创建视频任务（V1 允许的类型）
curl -X POST http://localhost:8080/api/video-tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"duration\":20,\"videoType\":\"pain_point_solution\"}"
# → 状态为 "analyzing"，等待 AI 回调

# 4. 模拟 AI 回调（推进状态）
TASK_ID="…"
curl -X POST http://localhost:8080/api/ai-callbacks/$TASK_ID \
  -H "Content-Type: application/json" \
  -H "X-Internal-Service-Token: internal-dev-token-change-in-production" \
  -d '{
    "taskId": "'$TASK_ID'",
    "schemaVersion": "1.0.0",
    "stage": "product_analysis",
    "status": "success",
    "nextTaskStatus": "analysis_completed",
    "productAnalysis": {"category": "Electronics", "sellingPoints": ["Noise cancelling", "Long battery"], "painPoints": ["Poor fit"], "targetAudience": ["Commuters"], "scenes": ["Office", "Gym"], "recommendedVideoTypes": ["pain_point_solution"], "videoScore": 78, "riskTips": [], "complianceNotes": ""}
  }'

# 5. 查看任务状态
curl http://localhost:8080/api/video-tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN"
# → status 应为 "analysis_completed"
```

### 8.8 项目结构速查

```
apps/api-java/src/main/java/com/tk/ai/video/
├── TkAiVideoApplication.java          # 启动入口
├── config/
│   ├── SecurityConfig.java            # Spring Security (JWT + 内部 Token + Argon2)
│   ├── JwtConfig.java                 # JWT 配置属性
│   ├── MyBatisPlusConfig.java         # Mapper 扫描
│   └── CosConfig.java                 # COS 配置属性
├── security/
│   ├── JwtTokenProvider.java          # JWT 签发/验证/解析
│   ├── JwtAuthenticationFilter.java   # OncePerRequestFilter
│   ├── InternalServiceTokenFilter.java # X-Internal-Service-Token 校验
│   ├── UserPrincipal.java            # UserDetails 实现
│   └── Role.java                     # USER / ADMIN 枚举
├── common/
│   ├── ApiResponse.java              # 统一响应包装
│   ├── PageResult.java               # 分页包装
│   ├── ErrorDetail.java              # 错误详情
│   ├── GlobalExceptionHandler.java   # @RestControllerAdvice
│   ├── CorrelationIdFilter.java      # 全链路追踪
│   ├── AiServiceClient.java          # Python AI HTTP 客户端
│   └── *Exception.java              # 业务异常类
└── module/
    ├── auth/        # 注册/登录/Token 刷新
    ├── user/        # 用户信息
    ├── product/     # 商品 CRUD
    ├── videotask/   # 视频任务 + 状态机
    ├── storyboard/  # 方案/分镜/镜头
    ├── video/       # 成品视频
    ├── quota/       # 额度管理
    ├── storage/     # COS 上传
    ├── callback/    # AI + Render 回调
    ├── admin/       # 管理后台
    └── log/         # 模型/渲染日志
```

---

> 文档结束。阶段 2 总计 **108 个 Java 源文件**，覆盖 11 个业务模块、~25 个 API 端点、完整状态机、幂等额度管理、JWT 认证体系。
>
> 下一阶段：**阶段 3（前端前半段）** 或 **阶段 4（Python AI 编排）** 可并行推进。
