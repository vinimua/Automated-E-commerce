# 服装 AI 创意循环 V1 技术方案

> 版本：1.0.0
> 日期：2026-06-27
> 来源需求：`docs/服装 AI 短视频创意生产系统 V1.0 需求文档.md`
> 定位：基于当前 TikTok Shop AI 视频项目的 V1.1 升级方案

## 1. 文档目标

本文档说明如何把“服装 AI 短视频创意生产系统”的需求落到当前项目架构中。

目标不是推翻现有项目，而是在已有“商品视频生成链路”上升级出一个带人工确认、关键帧控制、视频片段确认和反馈修复循环的服装视频生产工作台。

当前基础链路：

```text
商品上传
→ AI 分析
→ 视频方案
→ 用户选择方案
→ 脚本/分镜
→ 素材生成
→ Render Worker
→ 最终 MP4
```

目标链路：

```text
创建任务
→ 上传素材并绑定角色
→ 商品/参考视频分析
→ 创意方案
→ 用户确认方案
→ 脚本/分镜
→ 用户确认分镜
→ 配置关键帧
→ AI 生图或用户上传关键帧
→ 用户确认关键帧
→ AI 生成视频片段
→ 用户确认视频片段
→ Render Worker 合成成片
→ 成片预览
→ 反馈修复循环
```

## 2. 架构决策

现有服务边界保持不变。

```text
Next.js Web
  ↓ HTTP JWT
Java Spring Boot API + PostgreSQL
  ↓ 内部 HTTP
Python FastAPI + Temporal + 可选 LangGraph
  ↓ 第三方模型 API
图片生成 Provider / 视频生成 Provider
  ↓ 渲染消息
Node Render Worker + Remotion + FFmpeg
  ↓ 回调
Java Spring Boot API
```

### 2.1 不可突破的边界

1. Java Backend 是唯一业务状态源。
2. 前端只调用 Java API。
3. Python AI Orchestrator 不直接写 Java 业务表。
4. Render Worker 只消费 `RenderManifest` 和渲染任务。
5. 生图、生视频结果必须通过 Java 持有的记录落库。
6. 高成本视频生成必须经过用户显式确认。

## 3. 新增产品概念

### 3.1 任务模式

新增任务模式，用于区分用户从哪里开始生成。

```text
PRODUCT_CREATIVE       商品创意生成
REFERENCE_STORYBOARD   参考视频分镜迁移
USER_SCRIPT            用户脚本生成
CUSTOM_STORYBOARD      用户自定义分镜
```

建议字段：

```text
video_tasks.task_mode VARCHAR(80)
video_tasks.product_category VARCHAR(80) DEFAULT 'general'
video_tasks.current_version INT DEFAULT 1
```

### 3.2 素材角色

系统需要区分“用户上传的原始素材”和“系统生成的派生素材”。

原始素材角色：

```text
product_front
product_back
product_detail
model_reference
scene_reference
outfit_reference
reference_video
user_keyframe
generated_result
```

派生素材角色：

```text
ai_keyframe
image_variant
video_clip
final_video
cover_image
```

### 3.3 关键帧

视频生成需要已确认的关键帧，但关键帧不一定由 AI 生成。

关键帧来源包括：

1. 用户上传；
2. 从当前任务已有素材中选择；
3. AI 生图。

如果用户已经为某个镜头上传了关键帧，则该镜头可以跳过 AI 生图。

### 3.4 视频片段

AI 生视频按镜头生成，不直接生成整条完整视频。

示例：

```text
20 秒最终视频
→ 4 个分镜镜头
→ 4 张已确认关键帧
→ 4 个 AI 生成或用户上传的视频片段
→ Render Worker 合成最终 MP4
```

### 3.5 修复事件

用户反馈触发的修复必须记录为独立事件。

修复应尽量落到最小范围：

```text
storyboard
shot
keyframe
video_clip
render_manifest
final_video
```

## 4. 任务状态机

现有任务状态机偏线性，需要加入用户确认节点。

建议状态：

```text
draft
asset_uploading
asset_analyzing
waiting_asset_confirmation
reference_analyzing
plan_generating
waiting_plan_selection
storyboard_generating
waiting_storyboard_confirmation
keyframe_configuring
image_generating
waiting_image_confirmation
video_clip_generating
waiting_video_clip_confirmation
rendering
waiting_final_review
repairing
completed
failed
cancelled
exported
```

### 4.1 主状态流转

```text
draft
→ asset_uploading
→ asset_analyzing
→ waiting_asset_confirmation
→ plan_generating
→ waiting_plan_selection
→ storyboard_generating
→ waiting_storyboard_confirmation
→ keyframe_configuring
→ image_generating
→ waiting_image_confirmation
→ video_clip_generating
→ waiting_video_clip_confirmation
→ rendering
→ waiting_final_review
→ completed
→ exported
```

修复路径：

```text
waiting_final_review
→ repairing
→ waiting_storyboard_confirmation
或 waiting_image_confirmation
或 waiting_video_clip_confirmation
或 rendering
```

参考视频路径：

```text
asset_analyzing
→ reference_analyzing
→ waiting_asset_confirmation
```

规则：

1. 只有 Java 可以修改 `video_tasks.status`。
2. Python 回调可以建议 `nextTaskStatus`，但必须由 Java 校验是否合法。
3. 任意进行中状态可以转为 `failed`。
4. 高成本生成状态必须在用户确认后才能进入。

## 5. 数据库设计

现有表继续保留并复用：

```text
video_tasks
video_plans
storyboards
storyboard_shots
materials
videos
quota_records
model_logs
render_logs
```

通过 Flyway 新增以下表。

### 5.0 video_tasks 扩展字段

`video_tasks` 继续作为任务主状态表。Fashion Creative Loop 需要在任务级保存素材分析结果，避免复用 `products` 的 V1 商品分析字段。

```sql
ALTER TABLE video_tasks
    ADD COLUMN IF NOT EXISTS asset_analysis JSONB;
```

`asset_analysis` 保存 `asset_analysis` 回调返回的 `FashionAssetAnalysis`：

```json
{
  "schemaVersion": "1.0",
  "analysisText": "视觉模型对本次任务已确认素材的完整自然语言分析。",
  "analyzedAssetIds": ["asset-1", "asset-2"],
  "model": "vision-model",
  "analyzedAt": "2026-07-13T12:00:00Z"
}
```

设计约束：

1. `asset_analysis` 是任务级结果，属于当前视频任务，不写入 `products` 的品类、卖点、场景等 V1 遗留字段。
2. `analysisText` 是后续方案和分镜生成真正消费的内容。
3. `analyzedAssetIds`、`model`、`analyzedAt` 用于排查、判断素材是否变化和前端展示，不承担创意语义。
4. 如果 `analyzedAssetIds` 为空，说明本次分析没有读取到有效素材，不能视为素材分析链路完整通过。

### 5.1 task_assets

保存挂载到任务上的上传素材和生成素材。

```sql
CREATE TABLE task_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    asset_kind VARCHAR(50) NOT NULL,
    asset_role VARCHAR(80) NOT NULL,
    source VARCHAR(80) NOT NULL,
    url TEXT NOT NULL,
    file_name VARCHAR(255),
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    description TEXT,
    metadata JSONB,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 5.2 creative_states

保存服装任务的结构化状态。

```sql
CREATE TABLE creative_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL UNIQUE REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_json JSONB,
    model_json JSONB,
    scene_json JSONB,
    outfit_json JSONB,
    reference_video_json JSONB,
    constraints_json JSONB,
    user_requirements_json JSONB,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 5.3 keyframes

保存每个镜头的关键帧选择。

```sql
CREATE TABLE keyframes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    storyboard_id UUID REFERENCES storyboards(id) ON DELETE CASCADE,
    shot_id UUID REFERENCES storyboard_shots(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(80) NOT NULL,
    asset_id UUID REFERENCES task_assets(id) ON DELETE SET NULL,
    material_id UUID REFERENCES materials(id) ON DELETE SET NULL,
    image_purpose VARCHAR(80) NOT NULL DEFAULT 'first_frame',
    prompt TEXT,
    negative_prompt TEXT,
    user_instruction TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    version INT NOT NULL DEFAULT 1,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

建议状态：

```text
draft
generating
generated
uploaded
confirmed
rejected
failed
```

### 5.4 video_clips

保存每个镜头生成或上传的视频片段。

```sql
CREATE TABLE video_clips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    storyboard_id UUID REFERENCES storyboards(id) ON DELETE CASCADE,
    shot_id UUID REFERENCES storyboard_shots(id) ON DELETE CASCADE,
    keyframe_id UUID REFERENCES keyframes(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(80) NOT NULL,
    url TEXT,
    duration INT NOT NULL,
    prompt TEXT,
    negative_prompt TEXT,
    provider VARCHAR(100),
    model_name VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    quality_score INT,
    version INT NOT NULL DEFAULT 1,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

建议状态：

```text
draft
generating
generated
uploaded
confirmed
rejected
failed
```

### 5.5 repair_events

保存用户反馈、问题分类和修复决策。

```sql
CREATE TABLE repair_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(100),
    user_feedback TEXT NOT NULL,
    issue_type VARCHAR(100),
    repair_scope JSONB,
    repair_plan JSONB,
    before_version INT,
    after_version INT,
    status VARCHAR(50) NOT NULL DEFAULT 'created',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 5.6 video_fingerprints

保存视频结构指纹，用于去重。

```sql
CREATE TABLE video_fingerprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    opening_type VARCHAR(120),
    main_action VARCHAR(120),
    ending_type VARCHAR(120),
    main_visual VARCHAR(120),
    shot_sequence JSONB,
    camera_sequence JSONB,
    scene_position JSONB,
    similarity_score INT,
    compared_with JSONB,
    raw_fingerprint JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 5.7 qa_results

保存方案、分镜、Prompt、关键帧、视频片段和最终视频的质检结果。

```sql
CREATE TABLE qa_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES video_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(100),
    score INT,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    issues JSONB,
    suggestions JSONB,
    repair_instruction TEXT,
    raw_ai_output JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## 6. API 设计

所有接口都由 Java Backend 对前端提供。

### 6.1 创建任务

扩展现有接口：

```http
POST /api/video-tasks
```

新增字段：

```json
{
  "taskMode": "PRODUCT_CREATIVE",
  "productCategory": "fashion",
  "duration": 20,
  "shotCount": 4,
  "enableImageGeneration": true,
  "enableVideoGeneration": true,
  "userRequirement": "街头风，突出背面印花"
}
```

### 6.2 素材接口

```http
POST /api/video-tasks/{taskId}/assets
GET /api/video-tasks/{taskId}/assets
PATCH /api/video-tasks/{taskId}/assets/{assetId}/role
DELETE /api/video-tasks/{taskId}/assets/{assetId}
POST /api/video-tasks/{taskId}/assets/confirm
```

### 6.3 创意状态接口

```http
GET /api/video-tasks/{taskId}/creative-state
PATCH /api/video-tasks/{taskId}/creative-state
```

### 6.4 分析和方案接口

```http
POST /api/video-tasks/{taskId}/analyze-assets
POST /api/video-tasks/{taskId}/analyze-reference-video
POST /api/video-tasks/{taskId}/generate-plans
POST /api/video-tasks/{taskId}/confirm-plan
```

### 6.5 分镜接口

```http
POST /api/video-tasks/{taskId}/generate-storyboard
GET /api/video-tasks/{taskId}/storyboard
PATCH /api/storyboards/{storyboardId}
POST /api/video-tasks/{taskId}/confirm-storyboard
```

### 6.6 关键帧接口

```http
GET /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes/generate
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/confirm
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/reject
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate
```

### 6.7 视频片段接口

```http
GET /api/video-tasks/{taskId}/video-clips
POST /api/video-tasks/{taskId}/video-clips/generate
POST /api/video-tasks/{taskId}/video-clips/{clipId}/confirm
POST /api/video-tasks/{taskId}/video-clips/{clipId}/reject
POST /api/video-tasks/{taskId}/video-clips/{clipId}/regenerate
```

### 6.8 渲染和复审接口

```http
POST /api/video-tasks/{taskId}/render
POST /api/video-tasks/{taskId}/final-review/approve
POST /api/video-tasks/{taskId}/feedback
POST /api/video-tasks/{taskId}/repair
GET /api/video-tasks/{taskId}/repair-events
```

## 7. Python AI Orchestrator 设计

### 7.1 工作流拆分

不要把整条任务做成一个不可中断的大工作流。

建议工作流：

```text
FashionAnalysisWorkflow
  analyze_assets
  analyze_reference_video
  update_creative_state_callback

FashionPlanWorkflow
  generate_creative_plans
  generate_video_fingerprint
  dedup_check
  callback_java

FashionStoryboardWorkflow
  generate_script
  generate_storyboard
  prompt_compile
  qa_check
  callback_java

FashionKeyframeWorkflow
  generate_keyframe_prompts
  generate_images
  qa_images
  callback_java

FashionVideoClipWorkflow
  generate_video_prompts
  generate_video_clips
  qa_clips
  callback_java

FashionRepairWorkflow
  classify_feedback
  decide_repair_scope
  repair_target
  qa_check
  callback_java
```

### 7.2 LangGraph 使用范围

LangGraph 只用于 AI 决策循环，不用于承载整个业务工作流。

优先使用场景：

1. 用户反馈分类；
2. 修复路径选择；
3. QA 修复循环；
4. 去重后的分镜重写循环。

Temporal 继续负责长周期、可恢复、可重试的工作流。

### 7.3 Provider 拆分

模型配置必须区分文本、生图和生视频。

```env
TEXT_LLM_PROVIDER=openai
TEXT_LLM_MODEL=gpt-4o-mini

IMAGE_GEN_PROVIDER=
IMAGE_GEN_MODEL=
ENABLE_IMAGE_GENERATION=false

VIDEO_GEN_PROVIDER=
VIDEO_GEN_MODEL=
ENABLE_VIDEO_GENERATION=false
VIDEO_GEN_REQUIRE_APPROVAL=true
```

规则：

1. Text LLM 负责方案、脚本、分镜、Prompt、QA、修复。
2. Image Provider 负责关键帧图片。
3. Video Provider 负责短视频片段。
4. Render Worker 负责最终合成。

### 7.4 CreativeContext 组装

方案生成和分镜生成统一消费 Java 在运行时组装的 `CreativeContext`，不要让 Python 重新查询业务库，也不要让前端直接拼 AI 输入。

```json
{
  "productProfile": {},
  "userRequest": {
    "rawPrompt": "用户输入原文",
    "parsed": {},
    "confirmed": {}
  },
  "assetAnalysis": {
    "schemaVersion": "1.0",
    "analysisText": "素材分析得到的自然语言创意依据。",
    "analyzedAssetIds": ["asset-1"],
    "model": "vision-model",
    "analyzedAt": "2026-07-13T12:00:00Z"
  },
  "workflow": {
    "taskMode": "PRODUCT_CREATIVE",
    "durationSeconds": 20,
    "videoType": "product_showcase"
  }
}
```

消费规则：

1. `creative_plan` 必须把 `assetAnalysis.analysisText` 当作视觉证据来源，用于生成差异化方案。
2. `storyboard` 必须继续读取 `assetAnalysis.analysisText`，避免分镜凭空生成不可由素材支撑的画面。
3. `assetAnalysis` 不替代用户要求；用户最新要求仍放在 `userRequest`，并与素材分析一起进入 Prompt。
4. `CreativeContext` 是运行时对象，不单独建表持久化；持久化来源分别是 product、creative_state、video_tasks.asset_analysis 和任务参数。

## 8. AI 回调契约

扩展 AI 回调阶段：

```text
asset_analysis
reference_analysis
creative_plan
storyboard
keyframe
video_clip
qa
repair
render_manifest
```

回调结构继续保持：

```text
schemaVersion + stage + status + nextTaskStatus + error
```

每个阶段只携带该阶段相关结果，避免一个回调承载过多含义。

`asset_analysis` 成功回调必须携带 `fashionAssetAnalysis`，Java 持久化到 `video_tasks.asset_analysis`，并推进到 `waiting_asset_confirmation`。该回调不再复用 `products.selling_points`、`products.scenes`、`products.risk_tips` 等 V1 商品分析字段。

## 9. RenderManifest 策略

只有所有视频片段确认后，才构建 `RenderManifest`。

素材映射：

| 来源 | RenderManifest asset type |
| --- | --- |
| 已确认 video_clip | video |
| 已确认 keyframe fallback | image |
| 商品图 | product_image |
| 字幕/文字 | text |
| 音频/音乐 | audio |

Render Worker 不需要知道视频片段来自 AI 还是用户上传。

## 10. 前端路由设计

建议路由：

```text
/video-tasks/new
/video-tasks/{taskId}/assets
/video-tasks/{taskId}/reference-analysis
/video-tasks/{taskId}/plans
/video-tasks/{taskId}/storyboard
/video-tasks/{taskId}/keyframes
/video-tasks/{taskId}/clips
/video-tasks/{taskId}/review
```

页面由 `video_tasks.status` 驱动。

如果任务处于等待用户操作的状态，页面必须明确展示下一步动作。

## 11. 额度和成本设计

当前额度类型：

```text
video
image
video_clip
export
```

建议映射：

| 操作 | 额度类型 |
| --- | --- |
| 创建任务 | video |
| AI 关键帧生成成功 | image |
| AI 视频片段生成成功 | video_clip |
| 最终导出 | export |

规则：

1. 用户上传关键帧不消耗 image 额度。
2. 用户上传视频片段不消耗 video_clip 额度。
3. 单镜头重生成只消耗该镜头对应额度。
4. Provider 调用失败且没有可用产物时不扣额度。
5. 重复回调不能重复扣额度。

## 12. 实现顺序

1. 契约和数据库迁移。
2. Java 状态机和 API。
3. 前端素材、分镜、关键帧确认页面，先接假数据或假 Provider。
4. Python fake provider 工作流。
5. 接入关键帧生图 Provider。
6. 接入视频片段生成 Provider。
7. 使用已确认片段构建 `RenderManifest`。
8. 反馈修复和 LangGraph。
9. 参考视频分镜迁移。
10. 去重指纹和创意变体。

## 13. 测试策略

### 13.1 契约测试

1. 数据库状态枚举与 OpenAPI 一致。
2. AI callback fixture 通过 OpenAPI 校验。
3. RenderManifest fixture 通过 Worker validator。

### 13.2 后端测试

1. 状态流转校验；
2. 用户归属校验；
3. 关键帧确认；
4. 视频片段确认；
5. 额度幂等；
6. 修复事件持久化。

### 13.3 Python 测试

1. Pydantic Schema 校验；
2. fake provider 工作流；
3. 修复路由；
4. JSON 修复兜底；
5. 成本上限行为。

### 13.4 前端测试

1. 根据状态访问页面；
2. 上传和角色绑定；
3. 分镜编辑；
4. 上传关键帧跳过 AI 生图；
5. 片段确认后允许渲染。

### 13.5 端到端测试

最小 V1.1 E2E 路径：

```text
创建服装任务
→ 上传商品图和用户关键帧
→ 生成分镜
→ 确认分镜
→ 确认上传关键帧
→ fake 生成视频片段
→ 确认片段
→ 渲染最终 MP4
→ 确认成片
```

## 14. 灰度发布方案

使用功能开关：

```text
ENABLE_FASHION_CREATIVE_MODE
ENABLE_REFERENCE_VIDEO_ANALYSIS
ENABLE_IMAGE_GENERATION
ENABLE_VIDEO_GENERATION
ENABLE_LANGGRAPH_REPAIR
```

建议顺序：

1. 只启用 fake provider；
2. 启用用户上传关键帧；
3. 启用 AI 生图；
4. 启用需要确认的 AI 生视频；
5. 启用修复循环；
6. 启用参考视频分镜迁移。

## 15. 主要风险

| 风险 | 应对方式 |
| --- | --- |
| 状态机增长过快 | 每个里程碑只加入实际使用的状态 |
| API、DB、前端枚举不一致 | 契约优先，OpenAPI 生成类型 |
| 视频生成成本失控 | 用户确认 + 单镜头生成 |
| 模型输出 JSON 不稳定 | JSON Schema + Pydantic + 修复重试 |
| Render Worker 混入业务逻辑 | Worker 只消费 RenderManifest |
| 修复影响无关内容 | 保存修复范围并保留商品约束 |

## 16. 工程原则

服装创意循环应作为当前项目的受控扩展实现：

```text
不要替换现有流水线。
不要让 Python 持有业务状态。
不要让 Render Worker 理解商品逻辑。
不要在用户未确认时生成昂贵视频。
不要因为一个镜头错误就整条视频重来。
```
