# 服装 AI 创意循环 V1 AI 开发路线

> 版本：1.0.0
> 日期：2026-06-27
> 目的：给后续 AI 写代码时使用的分阶段开发路线
> 关联文档：
> - `docs/服装 AI 短视频创意生产系统 V1.0 需求文档.md`
> - `docs/Fashion-Creative-Loop-V1-Technical-Design.md`
> - `docs/01-database-schema.sql`
> - `docs/02-openapi-spec.yaml`
> - `docs/03-ai-output-json-schema.md`
> - `docs/04-render-manifest-schema.md`

## 1. 使用方式

本文档用于后续 AI 编码会话。

每次实现某个阶段前，必须先做以下事情：

1. 阅读相关契约文档；
2. 如果要新增枚举、字段、状态或 payload，先改契约再改代码；
3. 保持 Java Backend 是唯一业务状态源；
4. 保持前端只调用 Java API；
5. 保持 Python AI 只通过回调或 Java 持有的接口写结果；
6. 保持 Render Worker 只处理 `RenderManifest`。

不要在契约依赖未完成时提前实现后续阶段。

## 2. 里程碑总览

```text
M0 当前项目审计
M1 契约和数据库迁移
M2 Java 状态机和 API
M3 前端工作流壳子
M4 Python fake provider 工作流
M5 关键帧生图
M6 视频片段生成
M7 基于已确认片段渲染
M8 反馈修复循环
M9 参考视频分镜迁移
M10 去重和创意变体
M11 真实 Provider 加固和 E2E
```

## M0. 当前项目审计

目标：在修改代码前确认当前项目状态。

### 需要检查的文件

```text
docs/01-database-schema.sql
docs/02-openapi-spec.yaml
apps/api-java/src/main/java
apps/web/src/app
services/ai-orchestrator/src
apps/render-worker/src
```

### 任务

1. 列出 Java 和 OpenAPI 中当前任务状态。
2. 列出当前 AI callback stage。
3. 列出当前前端 video task 相关路由。
4. 列出当前 Python workflow 和 activity。
5. 列出当前 RenderManifest 字段和 Worker validator 规则。

### 完成标准

在开始 M1 前，输出一份简短审计说明。

## M1. 契约和数据库迁移

目标：先定义共享契约，再写服务代码。

### 契约变更

更新：

```text
docs/01-database-schema.sql
docs/02-openapi-spec.yaml
docs/03-ai-output-json-schema.md
```

新增或扩展：

```text
VideoTaskStatus
TaskMode
TaskAsset
CreativeState
Keyframe
VideoClip
RepairEvent
VideoFingerprint
QaResult
AI callback stages
```

### 代码变更

新增 Flyway migration：

```text
apps/api-java/src/main/resources/db/migration/V3__fashion_creative_loop.sql
```

新增表：

```text
task_assets
creative_states
keyframes
video_clips
repair_events
video_fingerprints
qa_results
```

扩展字段：

```text
video_tasks.task_mode
video_tasks.product_category
video_tasks.shot_count
video_tasks.current_version
```

### 测试

运行：

```text
cd apps/api-java
.\gradlew.bat test
```

### 完成标准

1. Flyway migration 可以正常执行。
2. Java 测试通过。
3. OpenAPI 包含新 schema 和新状态。
4. 代码没有使用契约中未定义的字段。

## M2. Java 状态机和 API

目标：让 Java 承载新的业务工作流。

### 后端模块

新增或扩展：

```text
VideoTask module
TaskAsset module
CreativeState module
Keyframe module
VideoClip module
RepairEvent module
Quota module
Callback module
```

### 需要实现的 API

```text
POST /api/video-tasks/{taskId}/assets
GET /api/video-tasks/{taskId}/assets
PATCH /api/video-tasks/{taskId}/assets/{assetId}/role
POST /api/video-tasks/{taskId}/assets/confirm

GET /api/video-tasks/{taskId}/creative-state
PATCH /api/video-tasks/{taskId}/creative-state

POST /api/video-tasks/{taskId}/confirm-plan
POST /api/video-tasks/{taskId}/confirm-storyboard

GET /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/confirm
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/reject

GET /api/video-tasks/{taskId}/video-clips
POST /api/video-tasks/{taskId}/video-clips/{clipId}/confirm
POST /api/video-tasks/{taskId}/video-clips/{clipId}/reject

POST /api/video-tasks/{taskId}/render
POST /api/video-tasks/{taskId}/feedback
GET /api/video-tasks/{taskId}/repair-events
```

### 状态规则

需要校验：

```text
waiting_storyboard_confirmation → keyframe_configuring
keyframe_configuring → image_generating
keyframe_configuring → waiting_image_confirmation
waiting_image_confirmation → video_clip_generating
waiting_video_clip_confirmation → rendering
waiting_final_review → completed
waiting_final_review → repairing
```

### 额度规则

1. 用户上传关键帧不消耗 image 额度。
2. 用户上传视频片段不消耗 video_clip 额度。
3. AI 生成关键帧成功后消耗 image 额度。
4. AI 生成视频片段成功后消耗 video_clip 额度。
5. 所有额度操作必须使用幂等键。

### 测试

新增后端测试：

1. 用户归属校验；
2. 状态流转校验；
3. 关键帧确认；
4. 视频片段确认；
5. 额度幂等；
6. 非法状态拒绝。

### 完成标准

Java 在不依赖 Python 的情况下，可以管理完整手动路径：

```text
创建任务
→ 添加素材
→ 确认分镜
→ 添加用户上传关键帧
→ 添加用户上传视频片段
→ 接受渲染请求
```

## M3. 前端工作流壳子

目标：用 Java API 暴露 human-in-the-loop 工作流。

### 路由

新增：

```text
/video-tasks/[id]/assets
/video-tasks/[id]/reference-analysis
/video-tasks/[id]/plans
/video-tasks/[id]/storyboard
/video-tasks/[id]/keyframes
/video-tasks/[id]/clips
/video-tasks/[id]/review
```

### UI 要求

1. 页面内容由任务状态驱动。
2. 明确展示下一步用户操作。
3. 关键帧页面支持每个镜头：
   - 上传图片；
   - 选择已有素材；
   - 请求 AI 生成；
   - 确认；
   - 驳回。
4. 视频片段页面支持每个镜头：
   - 预览片段；
   - 确认；
   - 驳回；
   - 请求重生成。

### 测试

运行：

```text
cd apps/web
npm run build
```

### 完成标准

1. 前端构建通过。
2. 用户可以通过 mock 或真实 Java API 跑通手动工作流。
3. 前端没有直接调用 Python 或 Render Worker。

## M4. Python fake provider 工作流

目标：在不消耗真实模型费用的情况下打通编排。

### 新增工作流

```text
FashionAnalysisWorkflow
FashionPlanWorkflow
FashionStoryboardWorkflow
FashionKeyframeWorkflow
FashionVideoClipWorkflow
FashionRepairWorkflow
```

### 新增 Activity

```text
analyze_fashion_assets
analyze_reference_video
generate_fashion_plans
generate_fashion_storyboard
generate_keyframe_prompts
fake_generate_keyframes
generate_video_clip_prompts
fake_generate_video_clips
classify_feedback
plan_repair
callback_java
```

### 配置

使用功能开关：

```env
ENABLE_IMAGE_GENERATION=false
ENABLE_VIDEO_GENERATION=false
ENABLE_LANGGRAPH_REPAIR=false
```

### 测试

1. 单测每个 Pydantic Schema。
2. 用 fixture 跑通 workflow。
3. 校验 callback payload。

### 完成标准

任务可以通过回调生成 fake keyframes 和 fake video clips。

## M5. 关键帧生图

目标：支持 AI 生图，同时允许用户上传图片跳过生图。

### 后端

实现：

```text
POST /api/video-tasks/{taskId}/keyframes/generate
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate
```

Java 必须校验：

1. 分镜已确认；
2. 目标镜头存在；
3. 用户有 image 额度；
4. 任务处于正确状态。

### Python

在以下适配器后接入真实 Provider：

```text
ImageGenerationProvider
```

同时保留 fake provider。

### 前端

每个镜头卡片提供：

```text
[上传图片] [选择已有素材] [AI 生成] [确认]
```

### 测试

1. 用户上传关键帧不会调用图片 Provider。
2. AI 生成关键帧会记录 provider/model。
3. 生图失败会产生 failed 状态和可读错误。
4. 重复回调不会重复扣额度。

### 完成标准

用户可以在同一个分镜中混合使用上传关键帧和 AI 生成关键帧。

## M6. 视频片段生成

目标：基于已确认关键帧生成短视频片段。

### 后端

实现：

```text
POST /api/video-tasks/{taskId}/video-clips/generate
POST /api/video-tasks/{taskId}/video-clips/{clipId}/regenerate
```

Java 必须校验：

1. 每个目标镜头都有已确认关键帧；
2. 用户明确请求生成；
3. 用户有 video_clip 额度；
4. 任务处于正确状态。

### Python

实现 Provider 适配器：

```text
VideoGenerationProvider
```

Provider 输入必须包含：

1. keyframe URL；
2. 镜头脚本；
3. 动作；
4. 运镜/机位；
5. 时长；
6. 商品约束；
7. negative prompt。

### 测试

1. 缺少关键帧时禁止生视频。
2. 单镜头重生成只消耗一个 clip 额度。
3. 用户上传视频片段会绕过视频 Provider。
4. 单个片段失败不影响其他已确认片段。

### 完成标准

每个分镜镜头都可以拥有一个已确认视频片段。

## M7. 基于已确认片段渲染

目标：只用已确认的视频片段合成最终视频。

### Java

实现：

```text
POST /api/video-tasks/{taskId}/render
```

规则：

1. 所有必需片段必须已确认；
2. Java 创建 `renderTaskId`；
3. Java 投递 `RenderManifest` 或调用现有渲染路径；
4. 状态变为 `rendering`；
5. 渲染回调后状态变为 `waiting_final_review`。

### Python

根据已确认 `video_clips` 和分镜文本构建 `RenderManifest`。

### Render Worker

只做最小改动：

1. 接受由 clip 生成的视频素材；
2. 保留现有校验；
3. 渲染 MP4 和封面。

### 测试

1. 任意镜头没有已确认片段时阻止渲染。
2. RenderManifest fixture 通过 validator。
3. Render callback 幂等。

### 完成标准

已确认片段可以渲染成最终 MP4，并在 review 页面展示。

## M8. 反馈修复循环

目标：对用户反馈进行分类，并只修复受影响范围。

### 后端

实现：

```text
POST /api/video-tasks/{taskId}/feedback
POST /api/video-tasks/{taskId}/repair
GET /api/video-tasks/{taskId}/repair-events
```

需要持久化：

```text
repair_events
qa_results
version changes
```

### Python

实现 `FashionRepairWorkflow`。

可选 LangGraph 节点：

```text
classify_feedback
locate_target
select_repair_strategy
repair_storyboard
repair_keyframe_prompt
repair_video_clip_prompt
qa_repaired_output
decide_next_status
```

### 规则

1. 最多自动修复 2 轮。
2. 昂贵重生成仍需要用户确认。
3. 修复必须保留商品约束。
4. 修复必须记录 before/after version。

### 测试

1. “背面印花没展示”路由到分镜/镜头修复。
2. “光线不对”路由到关键帧 Prompt 修复。
3. “动作太僵硬”路由到视频片段 Prompt 修复。
4. 修复不会擦掉商品约束。

### 完成标准

成片反馈可以生成局部修复计划，并把任务退回正确确认节点。

## M9. 参考视频分镜迁移

目标：支持“按照这个视频的分镜生成脚本”。

### 后端

实现：

```text
POST /api/video-tasks/{taskId}/analyze-reference-video
```

### Python

实现：

```text
analyze_reference_video
extract_reference_storyboard
transfer_storyboard_to_product
```

输出内容：

```text
duration
shot count
time ranges
hook
actions
camera
motion
lighting
CTA
reusable structure
```

### 前端

参考视频分析页展示提取出来的镜头，并允许用户在迁移前编辑。

### 测试

1. 参考视频 fixture 可以生成结构化镜头。
2. 分镜迁移保留当前商品卖点。
3. 用户可以编辑参考视频生成的分镜。

### 完成标准

用户可以上传参考视频，并基于该视频结构生成新商品分镜。

## M10. 去重和创意变体

目标：降低同一商品视频结构重复。

### 后端

持久化：

```text
video_fingerprints
similarity_score
compared_with
```

### Python

实现：

```text
generate_video_fingerprint
compare_recent_fingerprints
rewrite_repeated_structure
```

### 规则

相似度阈值：

```text
0-45 通过
46-70 存在风险，局部改写
71-100 必须重组结构
```

### 测试

1. 开场、主动作、结尾都相同时得到高分。
2. 重写后的结构至少修改 3 个变量。
3. 去重结果显示在方案 UI 中。

### 完成标准

创意方案包含去重分数，重复方案在用户选择前被改写。

## M11. 真实 Provider 加固和 E2E

目标：稳定真实生图、生视频和完整工作流。

### Provider 要求

1. 超时；
2. 重试策略；
3. 成本估算；
4. provider/model 日志；
5. 标准错误码；
6. 本地和开发环境保留 fake provider。

### E2E 路径

路径 A：用户上传关键帧。

```text
创建任务
→ 上传素材
→ 生成分镜
→ 上传关键帧
→ 确认关键帧
→ 生成视频片段
→ 确认片段
→ 渲染
→ 确认成片
```

路径 B：AI 生成关键帧。

```text
创建任务
→ 上传商品素材
→ 确认分镜
→ AI 生成关键帧
→ 确认关键帧
→ AI 生成视频片段
→ 渲染
```

路径 C：修复。

```text
成片预览
→ 用户反馈
→ 生成 repair event
→ 重生成一个受影响对象
→ 再次渲染
```

### 完成标准

1. Java 测试通过。
2. Web 构建通过。
3. Python workflow 测试通过。
4. Render Worker 构建通过。
5. 至少一条本地 fake provider E2E 路径跑通。
6. 真实 Provider 路径受功能开关控制，默认不自动运行。

## 3. 推荐给 AI 的开发 Prompt

使用小范围、明确边界的请求。

推荐：

```text
只实现 M1。更新契约，并新增 Flyway migration，包含 task_assets、creative_states、keyframes、video_clips、repair_events、video_fingerprints、qa_results。不要实现前端或 Python。运行 Java 测试。
```

推荐：

```text
只实现 M5 后端。按照 OpenAPI 契约新增 keyframe 生成、确认、驳回接口。先不要调用真实图片 Provider，使用 fake provider callback fixture。
```

避免：

```text
把整个 Fashion Creative Loop 系统一次性做完。
```

避免：

```text
到处都加 LangGraph。
```

## 4. 每个里程碑的完成定义

每个里程碑只有满足以下条件才算完成：

1. 相关契约已更新；
2. 受影响服务可以编译或构建；
3. 测试或目标验证已通过；
4. 如果 OpenAPI 变更，前端生成类型已刷新；
5. 没有服务违反边界；
6. 最终回复列出修改文件和验证结果。

## 5. 停止条件

遇到以下情况时停止并询问用户：

1. 需要真实 Provider API key，但尚未配置；
2. migration 可能破坏现有数据；
3. 现有生产数据状态与新状态冲突；
4. 会在没有明确确认的情况下产生视频生成费用；
5. 用户请求一次跨越多个里程碑且没有确认边界。

