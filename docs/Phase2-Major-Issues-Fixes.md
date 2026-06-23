# 阶段 2 主要问题与修复记录

> 日期：2026-06-13  
> 范围：Java Spring Boot 后端、阶段 2 契约文档、运行配置  
> 验证：`apps/api-java` 下执行 `.\gradlew.bat test`，结果通过

## 1. 选择方案接口会运行时崩溃

### 问题

`VideoTaskServiceImpl.selectPlan()` 调用 `AiServiceClient.startSelectedPlanGeneration()` 时传入的 `selectedPlan` 为 `null`。  
`AiServiceClient` 内部使用 `Map.of(...)` 构造请求体，而 `Map.of(...)` 不允许 key 或 value 为 `null`，因此用户选择方案后会触发 `NullPointerException`。

### 影响

- `/api/video-tasks/{taskId}/select-plan` 不可用。
- 用户无法从“方案选择”进入“脚本/分镜/素材生成”阶段。
- 后续阶段 3 前端和阶段 4 AI 编排会被该接口阻塞。

### 解决方案

- 在 `VideoTaskServiceImpl` 中读取真实 `VideoPlanEntity`。
- 将方案字段组装成非空 `selectedPlan` payload。
- `AiServiceClient` 改用 `LinkedHashMap` 构造请求体，并对 `selectedPlan == null` 做空 map 兜底。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java`
- `apps/api-java/src/main/java/com/tk/ai/video/common/AiServiceClient.java`

## 2. `planId` 没有校验归属

### 问题

选择方案时，原实现只校验任务归属，没有校验传入的 `planId` 是否属于当前任务和当前用户。

### 影响

- 用户可能提交其他任务的方案 ID。
- 理论上可能跨用户污染 `selected_plan_id`。
- 破坏用户数据隔离和任务状态一致性。

### 解决方案

- 新增 `VideoPlanMapper.findOwnedPlan(planId, taskId, userId)`。
- `selectPlan()` 必须通过该查询拿到方案，否则返回资源不存在。
- 新增单元测试覆盖“合法方案调度”和“非法方案拒绝”。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/storyboard/mapper/VideoPlanMapper.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java`
- `apps/api-java/src/test/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImplTest.java`

## 3. 状态机推进存在断点

### 问题

阶段 2 文档中的状态机是：

```text
analysis_completed -> plan_generated -> waiting_plan_selection -> script_generating
script_generated -> material_generating -> material_generated -> rendering -> checking -> completed
```

但原实现存在几个断点：

- `video_plan` 回调只推进到 `plan_generated`，没有进入 `waiting_plan_selection`。
- `selectPlan()` 只允许从 `waiting_plan_selection` 进入 `script_generating`，因此和前一个行为冲突。
- `material` 回调直接从 `script_generated` 推到 `material_generated`，跳过 `material_generating`。
- Render 成功回调插入视频后才尝试状态转换，副作用顺序不安全。

### 影响

- 用户选择方案可能被非法状态转换拦截。
- 素材回调无法按状态机推进。
- 渲染回调可能出现“视频已插入但任务状态未正确推进”的不一致。

### 解决方案

- `video_plan` 成功回调后连续推进到 `plan_generated` 和 `waiting_plan_selection`。
- `selectPlan()` 兼容从 `plan_generated` 自动推进到 `waiting_plan_selection` 后再进入 `script_generating`。
- `material` 回调先推进到 `material_generating`，再推进到 `material_generated`。
- Render 成功回调前置状态检查，并按 `rendering -> checking -> completed` 推进。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/RenderCallbackServiceImpl.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java`

## 4. AI 回调落库字段不完整

### 问题

`product_analysis` 回调原实现只保存了 `category`，没有保存契约中定义的其他商品分析字段。

### 影响

- 前端无法展示完整 AI 分析结果。
- 商品后续生成阶段缺少卖点、痛点、受众、场景和风险提示等上下文。
- 文档描述与真实实现不一致。

### 解决方案

补齐以下字段落库：

- `sellingPoints`
- `painPoints`
- `targetAudience`
- `scenes`
- `recommendedVideoTypes`
- `videoScore`
- `riskTips`

同时补齐 storyboard 回调中的 `coverText`、`musicSuggestion`、`hashtags`、`rawAiOutput`、`negativePrompt` 等字段处理。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java`

## 5. Render 回调不幂等

### 问题

原实现中，Render 成功回调会先插入 `videos`，再尝试检查/推进状态。重复回调时可能重复创建视频记录。

### 影响

- 同一个任务可能产生多条成片记录。
- 用户视频库会出现重复视频。
- 后续导出、额度、管理后台统计都会被污染。

### 解决方案

- 新增 `VideoMapper.findLatestByTaskId(taskId)`。
- Render 成功回调先判断是否已完成或已有视频。
- 重复成功回调直接返回，不重复插入。
- Render 失败回调也根据 `renderTaskId` 做重复处理。
- 成功和失败回调均写入 `render_logs` 便于诊断。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/video/mapper/VideoMapper.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/RenderCallbackServiceImpl.java`

## 6. 全局数据库完整性错误被错误返回为成功

### 问题

`GlobalExceptionHandler` 原先把所有 `DataIntegrityViolationException` 都当成幂等重放，返回 HTTP 200。

### 影响

这会掩盖真实的数据错误，例如：

- 非空约束失败
- 外键错误
- 唯一索引冲突
- CHECK 约束失败

前端和调用方会误以为操作成功，但数据库实际没有按预期写入。

### 解决方案

- 全局 `DataIntegrityViolationException` 改为 HTTP 409。
- 返回统一错误响应，不再伪装成成功。
- 真正需要幂等成功的场景，放在具体业务逻辑中显式处理。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/common/GlobalExceptionHandler.java`

## 7. 分镜编辑接口会触发唯一索引冲突

### 问题

`PATCH /api/storyboards/{id}` 更新 `shots` 时，原实现直接插入新镜头，没有删除旧镜头，也没有 upsert。  
数据库存在唯一索引 `(storyboard_id, shot_no)`，因此编辑已有镜头会冲突。

### 影响

- 分镜编辑接口在常见编辑场景下不可用。
- 如果叠加旧的全局异常处理，还可能表现为“接口成功但数据未更新”。

### 解决方案

- 新增 `StoryboardShotMapper.deleteByStoryboardId(storyboardId)`。
- 请求包含 `shots` 时，先删除旧镜头，再批量插入新镜头。
- 给 `StoryboardShotDto` 增加基础校验。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/storyboard/mapper/StoryboardShotMapper.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/storyboard/service/impl/StoryboardServiceImpl.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/storyboard/dto/StoryboardShotDto.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/storyboard/dto/UpdateStoryboardRequest.java`

## 8. 回调 DTO 缺少基础校验

### 问题

AI 回调和 Render 回调 DTO 没有 `@Valid`、`@NotNull`、`@Pattern` 等基础校验。

### 影响

- `stage`、`status`、`schemaVersion` 可以传入非法值。
- 业务层依赖字符串分支兜底，错误请求可能进入业务处理。
- 契约约束没有真正落到 Java API 层。

### 解决方案

- Controller 对回调请求启用 `@Valid`。
- `AiCallbackRequest` 校验：
  - `taskId` 必填
  - `schemaVersion` 固定 `1.0.0`
  - `stage` 限定为契约枚举
  - `status` 限定为 `success|failed`
- `RenderCallbackRequest` 校验：
  - `taskId` 必填
  - `status` 限定为 `completed|failed`

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/controller/CallbackController.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/dto/AiCallbackRequest.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/dto/RenderCallbackRequest.java`

## 9. 重试接口只改状态，不重新调度

### 问题

原 `retry()` 逻辑只退款、重置状态，没有重新调用 AI 服务或渲染调度。

### 影响

- 用户点击重试后，任务状态会变化，但后续流程不会继续运行。
- 任务可能长期卡在 `analyzing`、`script_generating`、`material_generating` 或 `rendering`。

### 解决方案

- 根据 `failedStage` 映射出的 `retryTarget` 重新调度：
  - `analyzing`：重新调用 `startProductAnalysis()`
  - `script_generating`、`material_generating`、`rendering`：重新调用 `startSelectedPlanGeneration()`
- 如果任务缺少 `selectedPlanId`，直接返回业务错误。

### 仍需注意

当前 Java 后端尚未实现 Render Worker 投递队列能力，因此 `rendering` 的重试暂时仍会重新触发 selected plan generation，让 AI 重新生成后续结果。真正的“只重投渲染任务”应在 Render Worker 阶段补齐。

### 涉及文件

- `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java`

## 10. 契约和文档漂移

### 问题

阶段文档和数据库契约存在不一致：

- 文档写“14 张表”，但 Flyway 迁移真实包含 `refresh_tokens`，总计 15 张。
- OpenAPI 的 `VideoType` 暴露 6 种，但 Java V1 实际只允许 3 种。
- RabbitMQ 文档中的 UI 端口说明和默认配置存在混淆。

### 影响

- 前端按 OpenAPI 生成类型后，可能提交后端不接受的视频类型。
- 后续开发者会误判数据库结构。
- 默认 RabbitMQ 端口可能连到管理 UI 而不是 AMQP 服务。

### 解决方案

- `docs/01-database-schema.sql` 补齐 `refresh_tokens` 表说明。
- `docs/Phase2-Implementation-Documentation.md` 将表数量修正为 15。
- `docs/02-openapi-spec.yaml` 将 V1 `VideoType` 收紧为：
  - `pain_point_solution`
  - `before_after`
  - `review`
- `application.yml` 将数据库和 RabbitMQ 连接改成环境变量可覆盖。
- RabbitMQ 默认端口改为 `25673`。

### 涉及文件

- `docs/01-database-schema.sql`
- `docs/02-openapi-spec.yaml`
- `docs/Phase2-Implementation-Documentation.md`
- `apps/api-java/src/main/resources/application.yml`

## 11. 新增测试

### 覆盖内容

新增 `VideoTaskServiceImplTest`，覆盖：

- 合法 `planId` 会组装完整 selected plan payload 并调度 AI。
- 非当前任务/用户的 `planId` 会被拒绝，且不会调度 AI。

### 涉及文件

- `apps/api-java/src/test/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImplTest.java`

## 12. 验证结果

在 `apps/api-java` 目录执行：

```powershell
.\gradlew.bat test
```

结果：

```text
BUILD SUCCESSFUL
```

## 13. 后续建议

当前修复解决了阶段 2 中会直接阻塞联调的主要问题，但仍建议继续补以下内容：

- 为 AI callback、Render callback、分镜更新、额度幂等补集成测试。
- 实现 Java 到 Render Worker 的渲染任务投递能力。
- 为 callback payload 引入更完整的 JSON Schema 校验，而不仅是基础 Bean Validation。
- 将开发环境默认密码从配置文件中迁移到 `.env` 或部署密钥管理。
