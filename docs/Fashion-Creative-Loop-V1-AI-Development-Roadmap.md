# 服装 AI 创意循环 V1 AI 开发路线图
> 版本：2.0.0  
> 日期：2026-07-09  
> 目的：给后续 AI 编码会话使用的、与前端工作流对齐且可逐步验收的开发路线。  
> 主要 UI 契约：`docs/Fashion-Creative-Loop-V1-User-Workflow-And-UI.md`

---

## 0. 为什么要调整这份路线图

旧路线图主要按后端和 AI 能力分层：

```text
契约 -> Java API -> 前端壳子 -> Python fake workflow -> 关键帧 -> 视频片段 -> 渲染 -> 修复
```

这个顺序从工程分层上看有一定道理，但实际开发时会造成一个问题：前端已经按照 `Fashion-Creative-Loop-V1-User-Workflow-And-UI.md` 设计页面，而旧路线没有保证每个页面在完成时都有足够的 Java API、fake AI 数据和状态流转支撑测试。

新版路线图改成一个原则：

```text
每个里程碑都必须交付一个前端可测试的用户切片。
```

一个里程碑不能只因为后端代码或 Python workflow 写完了就算完成。只有当前端页面能打开、能点击、能通过 Java API 看到正确状态和数据时，才算完成。

---

## 1. 信息源与优先级

### 1.1 UI 与页面流程

前端用户工作流以这份文档为准：

```text
docs/Fashion-Creative-Loop-V1-User-Workflow-And-UI.md
```

它决定：

1. `/products/new` 的任务创建流程
2. `taskMode` 的选择方式
3. 每个页面的职责边界
4. 状态驱动的路由跳转
5. 轮询行为
6. 页面按钮和用户操作

### 1.2 跨服务契约

实现仍然必须遵守项目契约：

```text
docs/01-database-schema.sql
docs/02-openapi-spec.yaml
docs/03-ai-output-json-schema.md
docs/04-render-manifest-schema.md
```

规则：

1. 前端只能调用 Java API。
2. Java 是用户可见任务状态的唯一来源。
3. Python AI Orchestrator 只能通过 callback 上报结果。
4. Render Worker 只消费 `RenderManifest`。
5. 只要 OpenAPI 改了，就必须重新生成前端类型。

---

## 2. 核心产品流程

V1 用户流程分为两个区域：

```text
/products/new
  -> 收集创建任务所需的最小输入
  -> 创建成功后跳转到 /video-tasks/{taskId}/assets

/video-tasks/{taskId}/...
  -> assets
  -> 可选 reference-analysis
  -> plans
  -> progress / storyboard
  -> keyframes
  -> clips
  -> review
```

前端必须由后端状态驱动，不应该在本地自行发明工作流状态。

---

## 3. taskMode 规则

创建页第一层选择必须是 `taskMode`，也就是用户想用哪种创作方式。

| taskMode | 用户含义 | 创建页最小输入 | 工作流差异 |
|---|---|---|---|
| `PRODUCT_CREATIVE` | 根据商品素材生成创意 | 商品名称 + 至少 1 张商品图 | 标准 7 页流程 |
| `REFERENCE_STORYBOARD` | 按参考视频结构生成 | 商品名称 + 参考视频 URL | 多一个 `/reference-analysis` 页面 |
| `USER_SCRIPT` | 用户已有脚本 | 商品名称 + 脚本文本 | 脚本写入 creative state |
| `CUSTOM_STORYBOARD` | 用户已有分镜 | 商品名称 + 分镜文本 | 分镜需求写入 creative state |

`videoType` 不是 V1 创建页的第一层用户选择。它是业务策略或 AI 生成策略字段，不应该再作为主要创作入口展示给用户。

### 3.1 防模板化设计边界

路线图后续阶段按下面的职责分层推进，避免把系统重新做成“选择模板 -> 套商品图”的产品：

| 层级 | 路线图要求 |
|---|---|
| `taskMode` | 只负责用户入口和流程分支，例如商品创意、参考视频、用户脚本、用户分镜 |
| `creative plan` | 负责差异化，必须输出不同角度、钩子、节奏、素材使用方式和说服路径 |
| `storyboard` | 负责具体镜头结构，每个方案都要能形成可编辑、可确认的分镜 |
| `template` | 只负责最终渲染样式，不反向决定创意方案 |

因此，前端不得新增与 `taskMode` 并列的 `videoType` 主选择器；AI 生成方案时可以输出 `type` 作为内部策略标签，但不能把它当成固定模板。

---

## 4. 里程碑完成标准

每个里程碑必须同时满足以下条件：

1. 相关 OpenAPI 契约已更新。
2. 如果 OpenAPI 有变化，前端生成类型已刷新。
3. Java 可以编译并通过测试。
4. 前端 build 通过。
5. 目标页面可以通过 Java API 手工测试。
6. 除非阶段明确要求真实 Provider，否则必须有 fake AI 或确定性数据支撑测试。
7. 阶段验收说明必须列出测试过的页面和命令。

推荐验证命令：

```bash
cd apps/api-java && ./gradlew.bat test
cd apps/web && npm run generate:api-types
cd apps/web && npm run build
cd services/ai-orchestrator && python -m pytest tests/ -q
```

---

## 5. 新里程碑路线

## M0. 契约与 UI 对齐审计

目标：在继续加功能前，确认 UI 工作流文档、数据库 Schema、OpenAPI、Java 状态机和前端路由一致。

### 范围

读取并对照：

```text
docs/Fashion-Creative-Loop-V1-User-Workflow-And-UI.md
docs/01-database-schema.sql
docs/02-openapi-spec.yaml
apps/web/src/app
apps/api-java/src/main/java
services/ai-orchestrator/src
```

### 必须检查

1. UI 使用到的所有任务状态都存在于 DB/OpenAPI/Java。
2. UI 文档里的所有前端路由都已存在，或明确列入后续计划。
3. `taskMode` 已存在于 DB/OpenAPI/Java/前端类型。
4. `/products/new` 不再把 `videoType` 当成主要用户选择。
5. Dashboard 根据任务状态跳转到正确页面。

### 前端可验收输出

不要求新增 UI，但必须输出一份简短审计文档：

```text
docs/Phase/M0-UI-Contract-Audit.md
```

---

## M1. 创建任务入口切片

目标：让 `/products/new` 完全按 UI 工作流文档可测试。

### 用户切片

```text
/products/new
  -> 选择 taskMode
  -> 输入当前模式所需的最小数据
  -> 创建 product + video task + creative state + 初始 assets
  -> 跳转到 /video-tasks/{taskId}/assets
```

### 后端/API

推荐聚合接口：

```http
POST /api/fashion-video-tasks
```

该接口应在一次事务中创建：

1. product
2. video task
3. creative state
4. initial task assets

如果聚合接口暂未实现，可以临时保留现有多接口调用，但前端必须明确提示中间失败，例如 product/task 创建成功但 initial context 保存失败。

### 契约

需要更新：

```text
docs/02-openapi-spec.yaml
apps/web/src/types/api.generated.ts
```

### 前端验收

1. 用户可以选择 4 个 `taskMode`。
2. `PRODUCT_CREATIVE` 要求至少 1 张商品图。
3. `REFERENCE_STORYBOARD` 要求参考视频 URL。
4. `USER_SCRIPT` 要求脚本文本。
5. `CUSTOM_STORYBOARD` 要求分镜文本。
6. 创建成功后跳转到 `/video-tasks/{taskId}/assets`。

---

## M2. 素材页与素材确认切片

目标：让 `/assets` 成为第一个真正可测试的工作流页面。

### 用户切片

```text
/video-tasks/{taskId}/assets
  -> 展示创建页带入的初始素材
  -> 支持继续添加商品图、模特图、场景图、参考视频等素材
  -> 用户确认素材
  -> 进入 asset_analyzing
  -> fake/real callback 返回 FashionAssetAnalysis
  -> 进入 waiting_asset_confirmation
  -> 用户再次确认
  -> 根据是否有 reference_video 跳转到 reference-analysis 或 plans
```

### 后端/API

必需接口：

```http
GET /api/video-tasks/{taskId}
GET /api/video-tasks/{taskId}/assets
POST /api/video-tasks/{taskId}/assets
PATCH /api/video-tasks/{taskId}/assets/{assetId}/role
DELETE /api/video-tasks/{taskId}/assets/{assetId}
POST /api/video-tasks/{taskId}/assets/confirm
```

### AI/Fake 行为

素材确认后：

1. Java 设置状态为 `asset_analyzing`。
2. Python fake workflow、真实 vision workflow 或测试 callback 返回 `asset_analysis`。
3. `asset_analysis` payload 必须包含 `fashionAssetAnalysis`，字段为 `schemaVersion`、`analysisText`、`analyzedAssetIds`、`model`、`analyzedAt`。
4. Java 将 `fashionAssetAnalysis` 保存到 `video_tasks.asset_analysis`，并设置状态为 `waiting_asset_confirmation`。
5. 不再把素材分析结果写入 `products.selling_points`、`products.scenes`、`products.risk_tips` 等 V1 遗留商品分析字段。
6. 第二次确认素材时：
   - 有 `reference_video` -> `reference_analyzing`
   - 无 `reference_video` -> `plan_generating`

`analysisText` 是后续 AI 真正消费的内容；`analyzedAssetIds`、`model`、`analyzedAt` 用于前端展示、排查和判断素材是否变化。

### 前端验收

1. `/assets` 能展示从 `/products/new` 带入的素材。
2. 缺少商品图时，确认前有明确提示。
3. 确认按钮返回预期状态。
4. `asset_analyzing` 期间页面每 5 秒轮询。
5. `waiting_asset_confirmation` 时展示 `assetAnalysis.analysisText`、模型、已分析素材数量和分析时间。
6. 如果 `analyzedAssetIds.length === 0`，页面必须显示警告，提示本次分析没有读取到有效素材。
7. 前端只根据后端返回状态跳转。

---

## M3. 方案与分镜切片

目标：在做关键帧前，让 `/plans`、`/progress`、`/storyboard` 可测试。

### 用户切片

```text
plan_generating
  -> /plans 显示加载中
  -> fake callback 创建方案
  -> waiting_plan_selection
  -> 用户选择方案
  -> storyboard_generating
  -> /progress 显示生成进度
  -> fake callback 创建分镜
  -> waiting_storyboard_confirmation
  -> /storyboard 支持查看、编辑、确认
  -> keyframe_configuring
```

### 后端/API

必需接口：

```http
GET /api/video-tasks/{taskId}/plans
POST /api/video-tasks/{taskId}/plans/{planId}/select
GET /api/video-tasks/{taskId}/storyboard
PATCH /api/video-tasks/{taskId}/storyboard
POST /api/video-tasks/{taskId}/confirm-storyboard
```

### AI/Fake 行为

fake workflow 必须稳定返回：

1. 2-3 个方案
2. 被选中的方案
3. 带 shots 的 storyboard
4. 合法 callback payload

方案生成与分镜生成必须通过运行时 `CreativeContext` 消费前置素材分析：

```text
productProfile + userRequest + assetAnalysis.analysisText + workflow
```

其中 `assetAnalysis` 来自 `video_tasks.asset_analysis`。如果存在 `assetAnalysis.analysisText`，`creative_plan` 和 `storyboard` Prompt 都必须显式引用它，避免方案和分镜脱离已确认素材。

### 前端验收

1. `/plans` 可以显示加载态和方案卡片。
2. 选择方案后状态推进。
3. `/progress` 在分镜生成完成后提供 `/storyboard` 链接。
4. `/storyboard` 能展示并编辑高层字段。
5. 确认分镜后跳转到 `/keyframes`。

---

## M4. 关键帧页与 fake 生图切片

目标：在接入真实图片 Provider 前，让 `/keyframes` 使用 fake 生图完整可测。

这个阶段替代旧路线里“前端壳子”和“关键帧生成”分离太远的问题。

### 用户切片

```text
keyframe_configuring
  -> /keyframes 为每个 storyboard shot 展示一张卡片
  -> 用户上传图片或请求 AI 生成
  -> image_generating
  -> fake callback 写入 generated/failed keyframes
  -> waiting_image_confirmation
  -> 用户逐帧确认/驳回
  -> 全部确认后进入 video_clip_generating
```

### 后端/API

必需接口：

```http
GET /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes/generate
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/confirm
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/reject
```

### 重要状态规则

允许状态流转：

```text
keyframe_configuring -> image_generating
keyframe_configuring -> waiting_image_confirmation
image_generating -> waiting_image_confirmation
waiting_image_confirmation -> image_generating
waiting_image_confirmation -> video_clip_generating
```

`waiting_image_confirmation -> image_generating` 是必须的，因为用户可能在确认页对 rejected/failed/missing 的关键帧重新生成。

### AI/Fake 行为

1. `ENABLE_IMAGE_GENERATION=false` 时使用 fake provider。
2. Fake provider 返回确定性的图片 URL。
3. 失败项必须持久化为 `status=failed` 并保存 `errorMessage`。
4. 单帧 regenerate 只能影响一个 shot。
5. 批量 generate 只能生成缺失的 shots。

### 前端验收

1. 每个 storyboard shot 都有一张 keyframe 卡片。
2. 用户上传关键帧不会调用图片 Provider。
3. AI 生成时显示 `image_generating` 加载态。
4. callback 后能看到生成图片预览。
5. rejected/failed keyframe 可以重新生成。
6. 全部确认后跳转到 `/clips`。

---

## M5. 视频片段页与 fake 生视频切片

目标：使用已确认关键帧和 fake 视频片段生成，让 `/clips` 可测试。

### 用户切片

```text
video_clip_generating
  -> /clips 显示加载态
  -> fake callback 写入 video clips
  -> waiting_video_clip_confirmation
  -> 用户预览视频片段
  -> 用户确认/驳回片段
  -> 全部确认后进入 rendering
```

### 后端/API

必需接口：

```http
GET /api/video-tasks/{taskId}/video-clips
POST /api/video-tasks/{taskId}/video-clips/generate
POST /api/video-tasks/{taskId}/video-clips/{clipId}/regenerate
POST /api/video-tasks/{taskId}/video-clips/{clipId}/confirm
POST /api/video-tasks/{taskId}/video-clips/{clipId}/reject
```

### AI/Fake 行为

1. `ENABLE_VIDEO_GENERATION=false` 时使用 fake provider。
2. Fake 视频片段结果必须包含 URL、provider、modelName、prompt、status。
3. 失败片段必须保存为 `failed` 并带 `errorMessage`。
4. 单片段 regenerate 只能影响一个 clip。

### 前端验收

1. `/clips` 为每个已确认 keyframe/shot 展示一张卡片。
2. `video_clip_generating` 时显示加载态。
3. fake video URL 能进入 `<video>` 预览。
4. 确认/驳回按钮能更新卡片状态。
5. 全部确认后可以进入 render/review 流程。

---

## M6. 成片审核与渲染切片

目标：让 `/review` 能通过 fake 或本地渲染结果测试。

### 用户切片

```text
rendering
  -> /review 显示渲染中
  -> render callback 写入最终视频结果
  -> waiting_final_review
  -> 用户预览成片
  -> 批准 -> completed
```

### 后端/API

必需接口：

```http
POST /api/video-tasks/{taskId}/render
GET /api/video-tasks/{taskId}
GET /api/videos/{videoId}
POST /api/video-tasks/{taskId}/approve
```

### 渲染契约

渲染输入只能基于已确认的视频片段：

```text
confirmed video_clips -> RenderManifest -> Render Worker -> callback -> final video
```

### 前端验收

1. `/review` 在 `rendering` 状态显示加载态。
2. `waiting_final_review` 时显示最终视频预览。
3. 批准按钮能将任务标记为 `completed`。
4. 有可用下载/导出链接时必须显示。

---

## M7. 反馈修复循环切片

目标：让用户从 `/review` 提交反馈并完成局部修复。

### 用户切片

```text
waiting_final_review
  -> 用户提交反馈
  -> repairing
  -> AI 判断修复目标
  -> 任务回到 keyframe/video_clip/render 等具体阶段
  -> 用户确认修复结果
```

### 后端/API

必需接口：

```http
POST /api/video-tasks/{taskId}/feedback
GET /api/video-tasks/{taskId}/repair-events
```

### 修复路由

| targetType | 下一状态 |
|---|---|
| `keyframe` | `image_generating` |
| `video_clip` | `video_clip_generating` |
| `render_manifest` | `rendering` |
| `final_video` | `rendering` |
| fallback | `keyframe_configuring` |

### 前端验收

1. `/review` 有反馈表单。
2. 提交反馈后任务进入 `repairing`。
3. 页面能展示修复历史。
4. repair callback 后，前端能跳转到正确页面。
5. repair 不应默认重跑整条任务链路。

---

## M8. 四种 taskMode 完整打通

目标：让 `/products/new` 的 4 种 taskMode 都能通过 fake provider 跑通端到端。

### 范围

| taskMode | 必需行为 |
|---|---|
| `PRODUCT_CREATIVE` | 从商品素材出发生成方案 |
| `REFERENCE_STORYBOARD` | 在生成方案前分析参考视频 |
| `USER_SCRIPT` | 使用脚本文本作为创作输入 |
| `CUSTOM_STORYBOARD` | 使用分镜文本作为创作输入 |

### 验收

1. 每种 taskMode 都能创建任务。
2. 每种 taskMode 的初始输入都写入 `creative_state.userRequirements`。
3. 每种 taskMode 都走正确页面顺序。
4. fake AI 输出能体现所选 taskMode，至少足够手工测试。

---

## M9. 真实图片与视频 Provider

目标：在功能开关保护下，用真实 Provider 替换 fake provider。

### Provider 规则

1. 本地开发默认关闭真实 Provider。
2. Provider 调用必须有超时和重试策略。
3. 必须记录 provider/model/cost metadata。
4. 失败必须使用结构化错误字段。
5. 单个生成失败不能阻塞其他已成功 item。

### 功能开关

```env
ENABLE_IMAGE_GENERATION=true
ENABLE_VIDEO_GENERATION=true
```

### 验收

1. fake provider 路径仍然通过全部测试。
2. 真实图片 Provider 可以生成一张有效 URL 的 keyframe。
3. 真实视频 Provider 可以生成一个有效 URL 的 clip。
4. 额度消耗必须幂等。

---

## M10. 端到端加固

目标：验证前端、Java、Python、Render Worker 的完整工作流。

### E2E 路径

路径 A：商品创意 + 用户上传关键帧。

```text
create task -> assets -> plans -> storyboard -> upload keyframes -> clips -> render -> approve
```

路径 B：商品创意 + AI 生成关键帧和视频片段。

```text
create task -> assets -> plans -> storyboard -> AI keyframes -> AI clips -> render -> approve
```

路径 C：参考视频分镜模式。

```text
create task -> assets -> reference-analysis -> plans -> storyboard -> keyframes -> clips -> review
```

路径 D：修复循环。

```text
review -> feedback -> repair -> regenerate target -> confirm -> render again
```

### 验收

1. Java 测试通过。
2. Python 测试通过。
3. Web build 通过。
4. Render Worker build/tests 通过。
5. 至少一条 fake-provider E2E 路径可以本地跑通。
6. 真实 Provider 路径受功能开关保护。

---

## 6. 旧里程碑到新里程碑的映射

| 旧里程碑 | 新位置 | 原因 |
|---|---|---|
| M0 审计 | M0 | 保留 |
| M1 契约/数据库 | 分散到 M1-M8 | 契约应跟随每个用户可测切片一起变化 |
| M2 Java API/状态机 | 分散到 M1-M8 | Java endpoint 应该跟使用它的页面一起落地 |
| M3 前端壳子 | 不再单独存在 | 没有可测后端支撑的前端壳子价值有限 |
| M4 Python fake workflow | 分散到 M2-M7 | fake AI 应该服务每个页面切片 |
| M5 关键帧 | 新 M4 | keyframes 必须和 `/keyframes` 页面及 fake provider 一起验收 |
| M6 视频片段 | 新 M5 | clips 必须和 `/clips` 页面及 fake provider 一起验收 |
| M7 渲染 | 新 M6 | render 属于 `/review` 切片 |
| M8 修复 | 新 M7 | repair 属于 `/review` 反馈循环 |
| M9 参考视频迁移 | 新 M8 | reference 是 4 种 taskMode 之一 |
| M10 去重/创意变体 | 后续增强 | V1 前端 E2E 前不是必需项 |
| M11 真实 Provider/E2E | M9-M10 | 拆成真实 Provider 和完整加固两个阶段 |

---

## 7. 开发 Prompt 模板

### 推荐：创建入口

```text
只实现 M1。按 docs/Fashion-Creative-Loop-V1-User-Workflow-And-UI.md 对齐 /products/new。
先更新 OpenAPI，再刷新前端生成类型；如有必要，实现 Java 聚合创建任务接口；最后更新前端创建表单。
不要实现 keyframes、clips、render 或 repair。
运行 Java 测试和 Web build。
```

### 推荐：素材切片

```text
只实现 M2。让 /video-tasks/[id]/assets 可以通过 Java API 和 fake asset analysis callback 完整测试。
前端只能调用 Java。先更新契约再改代码。如果 callback schema 变化，运行 Python 测试；最后运行 Java 测试和 Web build。
```

### 推荐：关键帧切片

```text
只实现 M4。让 /video-tasks/[id]/keyframes 可以使用 fake image generation 测试。
批量生成只能生成缺失 shots；单帧 regenerate 只能影响一个 shot；failed item 必须落库。
先更新 OpenAPI 和 AI 输出 Schema，刷新前端类型，再实现 Java/Python/前端改动。
```

### 避免

```text
一次性做完整 Fashion Creative Loop 系统。
```

### 避免

```text
fake-provider UI 流程还没完整可测时就接真实图片/视频 Provider。
```

---

## 8. 停止条件

遇到以下情况时应停止并询问用户：

1. 当前里程碑需要真实 Provider API key。
2. migration 可能破坏已有本地数据。
3. 前端 UI 文档和 OpenAPI 对某个必填字段不一致。
4. 某个改动会让前端直接调用 Python 或 Render Worker。
5. 某个任务会跨越多个用户可测试切片。
