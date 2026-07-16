# M7 阶段结果与验收：反馈修复循环切片

提交日期：2026-07-16  
阶段：M7  
当前结论：已补齐反馈修复闭环的关键断点，M7 可以进入端到端验收。

## 1. M7 目标

让用户在 `/review` 对最终成片提交反馈后，系统可以根据反馈目标进入对应的局部修复流程：

```text
waiting_final_review
  -> 用户提交反馈
  -> repairing
  -> Python 分类反馈并生成 repair plan
  -> Java 根据 targetType 触发真实重生成/重渲染
  -> 用户确认修复结果
```

## 2. 本轮发现的问题

### 2.1 repair callback 只改状态，没有触发真实重生成

旧实现中，Python `FashionRepairWorkflow` 回调 Java 后，Java 只做：

```text
repairing -> image_generating / video_clip_generating / rendering
```

但没有真正调用：

- keyframe generation workflow
- video clip generation workflow
- render worker queue

结果是前端看起来进入生成中，实际没有任何生成任务在跑。

### 2.2 keyframe 修复会卡死

旧路由把 `keyframe` 修复目标直接推进到 `image_generating`，但已有 `generateKeyframes()` 入口只允许从：

```text
keyframe_configuring
waiting_image_confirmation
```

触发生成。进入 `image_generating` 后用户无法通过现有接口补救。

### 2.3 render/final_video 修复没有 renderTaskId 和队列消息

旧实现进入 `rendering` 后，没有生成新的 `renderTaskId`，也没有发送 RabbitMQ render task。  
因此 `/review` 会显示渲染中，但 render worker 没有任务可消费。

### 2.4 repair plan 没有传给后续模块

Python 返回的 `repairResult` 只保存到了 `repair_events.repair_plan`，没有进入后续 keyframe/video/render payload。  
这会导致后续模型不知道用户到底要求修哪里。

### 2.5 repair_events.status 语义不准确

旧实现会在 repair plan 回调时直接把 repair event 标记为 `completed`。  
但此时只是“修复计划生成完成”，不是“修复结果完成”。

### 2.6 契约不一致

OpenAPI 的 `FeedbackRequest` 缺少：

- `targetType`
- `targetId`

Python `RepairRequest` 也缺少 Java 实际发送的：

- `repairEventId`

## 3. 本轮修复方案

### 3.1 Java repair callback 改为真实调度

`AiCallbackServiceImpl` 新增 repair dispatch 逻辑：

```text
targetType = keyframe
  -> 创建新 currentVersion
  -> 复制未受影响 keyframes/clips
  -> affectedShots 创建 generating keyframe
  -> 调用 startKeyframeGeneration

targetType = video_clip
  -> 创建新 currentVersion
  -> 复制 confirmed keyframes
  -> 复制未受影响 clips
  -> affectedShots 创建 generating clip
  -> 调用 startVideoClipGeneration

targetType = render_manifest / final_video
  -> 复用当前 renderManifest
  -> 注入 repairContext
  -> 生成新的 renderTaskId
  -> 发送 render worker 队列
```

### 3.2 版本策略

只有会产生新素材的修复才提升 `currentVersion`：

| 修复目标 | 是否提升 currentVersion | 原因 |
|---|---:|---|
| keyframe | 是 | 会产生新关键帧 |
| video_clip | 是 | 会产生新视频片段 |
| render_manifest | 否 | 只重渲染，不改变素材版本 |
| final_video | 否 | 只重渲染，不改变素材版本 |

### 3.3 repair plan 传递给后续生成

后续生成 payload 会携带：

```json
{
  "repairContext": {
    "targetType": "video_clip",
    "affectedShots": [3],
    "strategy": "...",
    "repairNotes": "..."
  }
}
```

这样后续 prompt 生成/素材生成模块可以知道本轮修复目标。

### 3.4 repair event 状态语义修正

现在状态含义调整为：

```text
用户提交反馈 -> in_progress
repair plan 回调 -> 仍然 in_progress
keyframe/video_clip/render 完成回调 -> completed
render failed -> failed
```

### 3.5 契约同步

已同步：

- `docs/02-openapi-spec.yaml`
- `apps/web/src/types/api.generated.ts`
- `services/ai-orchestrator/src/schemas/workflow_requests.py`

## 4. 修改文件

| 文件 | 说明 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | repair callback 改为真实触发 keyframe/video_clip/render 修复 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/RenderCallbackServiceImpl.java` | render 成功/失败后收尾 repair event |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java` | 用户提交反馈后 repair event 进入 `in_progress` |
| `services/ai-orchestrator/src/schemas/workflow_requests.py` | `RepairRequest` 新增 `repairEventId` |
| `services/ai-orchestrator/src/api/workflows.py` | repair workflow 启动时把 `repairEventId` 合并进 currentState |
| `docs/02-openapi-spec.yaml` | `FeedbackRequest` 补充 `targetType` / `targetId` |
| `apps/web/src/types/api.generated.ts` | 同步前端 API 类型 |

## 5. 验证结果

已通过：

```text
apps/api-java       ./gradlew.bat compileJava
apps/web            npm run build
services/ai-orchestrator
  python -m pytest tests/test_schemas.py tests/test_workflows.py tests/test_callback_payloads.py
```

Python 测试结果：

```text
50 passed
```

## 6. 仍需端到端验收

代码链路已补齐，但仍建议手动做以下验收：

1. 生成成片进入 `waiting_final_review`
2. 在 `/review` 选择 `final_video` 提交反馈
3. 确认状态进入 `rendering`
4. 确认 render worker 收到新的 render task
5. render callback 后回到 `waiting_final_review`
6. repair event 从 `in_progress` 变为 `completed`
7. 对 `video_clip` 提交反馈，确认只重生成 affectedShots
8. 对 `keyframe` 提交反馈，确认进入关键帧确认页，并保留未受影响镜头素材

## 7. 当前结论

M7 的核心断点已经修复。  
之前的实现只是“反馈入口 + 状态跳转”，现在已经改成“反馈入口 + repair plan + 真实重生成/重渲染 + repair event 收尾”。

可以进入端到端验收。
