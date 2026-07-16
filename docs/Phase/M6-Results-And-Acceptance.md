# M6 阶段结果与验收：成片审核与渲染切片

提交日期：2026-07-16  
阶段：M6  
当前结论：代码完整就绪，回调链路补齐，需真实任务做端到端验收

## 1. M6 目标

让 `/review` 能通过 fake 或本地渲染结果测试：

```text
rendering
  -> /review 显示渲染中
  -> render callback 写入最终视频结果
  -> waiting_final_review
  -> 用户预览成片
  -> 批准 -> completed
```

## 2. 交付物清单

### 2.1 本阶段新增/修改文件

| 文件路径 | 类型 | 改动说明 |
|---|---|---|
| `apps/api-java/.../callback/dto/RenderCallbackRequest.java` | 修改 | 新增 `coverUrl`、`duration`、`renderLog` 字段 |
| `apps/api-java/.../callback/service/RenderCallbackService.java` | 新增 | 渲染回调服务接口 |
| `apps/api-java/.../callback/service/impl/RenderCallbackServiceImpl.java` | 新增 | 渲染回调处理：成功 → `waiting_final_review`，失败 → `failed` |

### 2.2 已有代码（M6 依赖，无需新写）

| 层 | 文件 | 说明 |
|---|---|---|
| Java | `VideoTaskController.POST /{taskId}/render` | 验证已确认 clips → 状态推进到 `rendering` |
| Java | `VideoTaskController.POST /{taskId}/approve` | 批准最终成片 → `completed` |
| Java | `CallbackController.POST /api/render-callbacks/{taskId}` | 接收 Render Worker 回调 |
| Java | `RenderMessageProducer` | 构建消息 → RabbitMQ `video.render.queue` |
| Java | `AiCallbackServiceImpl` (case "render_manifest") | 接收 Python 渲染清单 → 保存 → 推 RabbitMQ |
| Java | `VideoTaskStateMachine` | rendering → waiting_final_review → completed → repairing |
| Python | `build_render_manifest` activity | 从 storyboard + materials 构建 `RenderManifest` |
| Python | `SelectedPlanGenerationWorkflow` step 8 | build_render_manifest → callback_java |
| Python | `render_manifest.py` Pydantic schema | template 映射、素材校验、时长验证 |
| Node.js | `render-consumer.ts` | RabbitMQ 消费者 → 验证 manifest → 渲染 → 回调 |
| Node.js | `renderer.ts` | Remotion 渲染引擎 → MP4 + 封面 |
| Node.js | `ReviewV1.tsx` | Remotion 模板：5 段时间线结构 |
| 前端 | `/review/page.tsx` | 渲染中 spinner → 视频预览 → 批准/反馈按钮 |
| 前端 | Dashboard 路由 | rendering/waiting_final_review/repairing → /review |

## 3. 本轮发现并修复的问题

### 3.1 RenderCallbackRequest 字段不全

原问题：`RenderCallbackRequest` DTO 只有基本字段，Render Worker 回调时无法携带 `coverUrl`、`duration`、`renderLog` 等渲染结果信息。

修复：扩展 DTO，新增 `coverUrl`、`duration`、`renderLog` 字段。

### 3.2 渲染回调处理缺失

原问题：`CallbackController` 有接收端点，但 `RenderCallbackService` 和实现类不存在，编译失败。

修复：新增 `RenderCallbackService` 接口 + `RenderCallbackServiceImpl`，处理 `completed`/`failed` 两种回调状态，推进任务到 `waiting_final_review` 或 `failed`。

## 4. 状态流转验收

| 当前状态 | 触发动作 | 目标状态 | 实现位置 |
|---|---|---|---|
| `waiting_video_clip_confirmation` | 全部 clip 确认 + 用户点击渲染 | `rendering` | VideoTaskServiceImpl.requestRender |
| `rendering` | Render Worker 回调 success | `waiting_final_review` | RenderCallbackServiceImpl.handleCallback |
| `rendering` | Render Worker 回调 failed | `failed` | RenderCallbackServiceImpl.handleCallback |
| `waiting_final_review` | 用户点击批准 | `completed` | VideoTaskServiceImpl.approveFinalReview |
| `waiting_final_review` | 用户提交反馈 | `repairing` | VideoTaskServiceImpl.submitFeedback |
| `repairing` | 修复目标为 render_manifest/final_video | `rendering` | FashionRepairWorkflow |

## 5. API 端点速查

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/video-tasks/{taskId}/render` | 触发渲染（已有）|
| GET | `/api/video-tasks/{taskId}` | 获取任务（已有）|
| POST | `/api/video-tasks/{taskId}/approve` | 批准成片（已有）|
| POST | `/api/render-callbacks/{taskId}` | Render Worker 回调（补齐）|

## 6. 渲染链路

```
VideoClipServiceImpl.checkAndAdvanceAfterAllConfirmed
  → 全部 clip confirmed → 出现"开始渲染"按钮
  → POST /render → VideoTaskServiceImpl.requestRender
    → 验证所有 clip confirmed
    → status: "waiting_video_clip_confirmation" → "rendering"
    → progress: 90

Python AI (V1 path) 或本地构建 RenderManifest
  → RenderMessageProducer.sendRenderTask()
  → RabbitMQ: video.render.queue

Node.js render-worker:
  → validate manifest (Zod)
  → download assets (HTTP)
  → render video (Remotion)
  → upload to COS
  → callback: POST /api/render-callbacks/{taskId}

RenderCallbackServiceImpl.handleCallback:
  → completed: status → "waiting_final_review", progress → 95
  → failed: status → "failed"

前端 /review:
  → rendering: spinner
  → waiting_final_review: 视频播放器 + 批准 + 反馈
  → completed: 成功状态
```

## 7. 验证结果

- Java 编译通过：`./gradlew compileJava` BUILD SUCCESSFUL
- 渲染 Worker 完整：consumer → renderer → callback 三层就绪
- 前端 /review 页面完整：4 种状态展示（rendering / reviewing / repairing / completed）
- 审批/修复循环完整：approve → completed，feedback → repair → rendering

## 8. 仍需端到端验收

1. 全部 clip 确认后 → 出现 Render 按钮 → 点击 → 状态变为 `rendering`
2. Render Worker 启动 → 消费 RabbitMQ → 渲染 → 回调到 Java
3. `/review` 刷新 → 显示视频预览（`waiting_final_review`）
4. 点击批准 → `completed`，跳转 `/videos/{videoId}`
5. 点击反馈 → `repairing` → 修复后回到 `rendering`

## 9. 结论

M6 核心链路完整。前端和后端接口均已存在，本轮补齐了渲染回调处理的缺失部分。Render Worker 已可用，但 fake 模式下可跳过实际渲染——在 Dev 环境手动调一次 `POST /api/render-callbacks/{taskId}` 传入 video URL 即可模拟回调，验证完整 UI 流程。

当前状态：代码完整就绪，需端到端验证后标记为完全通过。
