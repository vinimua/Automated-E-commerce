# M7 问题与解决方案：反馈修复循环

日期：2026-07-16  
阶段：M7  
主题：`/review` 用户反馈后的局部修复闭环

## 1. 背景

M7 的目标不是单纯收集用户反馈，而是让用户在最终成片审核页提交反馈后，系统能够根据反馈目标自动进入对应的修复链路：

```text
waiting_final_review
  -> 用户提交反馈
  -> repairing
  -> AI 分类反馈并生成 repair plan
  -> Java 根据 repair plan 触发真实重生成/重渲染
  -> 用户重新确认修复结果
```

本轮检查发现，原实现已经有反馈入口、repair workflow 和状态跳转，但缺少真正的执行闭环。

## 2. 问题一：repair callback 只改状态，没有触发真实重生成

### 问题表现

原流程中，Python `FashionRepairWorkflow` 回调 Java 后，Java 只根据 `targetType` 把任务状态推进到：

```text
image_generating
video_clip_generating
rendering
keyframe_configuring
```

但没有真正调用对应的生成逻辑。

### 后果

前端会显示“生成中”或“渲染中”，但实际上：

- keyframe workflow 没有启动
- video clip workflow 没有启动
- render worker 没有收到任务
- 用户看到页面卡住

### 解决方案

在 `AiCallbackServiceImpl` 中新增 repair dispatch 逻辑，让 repair callback 不再只推进状态，而是根据 `targetType` 触发真实工作：

```text
targetType = keyframe
  -> 创建新版本
  -> 复制未受影响素材
  -> affectedShots 创建 generating keyframe
  -> 调用 startKeyframeGeneration

targetType = video_clip
  -> 创建新版本
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

## 3. 问题二：keyframe 修复会进入不可补救状态

### 问题表现

原实现把 `keyframe` 修复目标直接推进到：

```text
image_generating
```

但现有 `generateKeyframes()` 接口只允许从以下状态触发：

```text
keyframe_configuring
waiting_image_confirmation
```

### 后果

任务进入 `image_generating` 后，前端无法通过已有接口重新触发关键帧生成。

### 解决方案

repair callback 现在会直接创建新版本的 `generating` keyframe 记录，并主动调用：

```text
aiServiceClient.startKeyframeGeneration(...)
```

因此不再依赖用户在前端二次点击生成按钮。

## 4. 问题三：final_video / render_manifest 修复没有投递 render worker

### 问题表现

原实现会把任务状态改成：

```text
rendering
```

但没有：

- 生成新的 `renderTaskId`
- 更新 `renderManifest`
- 发送 RabbitMQ render task

### 后果

`/review` 页面显示渲染中，但 render worker 没有任务可消费。

### 解决方案

当 `targetType` 是：

```text
render_manifest
final_video
```

Java 会：

1. 复用当前 `renderManifest`
2. 注入 `repairContext`
3. 生成新的 `renderTaskId`
4. 更新任务状态为 `rendering`
5. 调用 `RenderMessageProducer.sendRenderTask(...)`

这样 render worker 可以真正重新渲染。

## 5. 问题四：repair plan 没有传给后续生成模块

### 问题表现

Python 返回的 `repairResult` 只保存到了：

```text
repair_events.repair_plan
```

但没有进入后续生成 payload。

### 后果

后续模型不知道用户本次到底要修什么，例如：

```text
第 3 镜商品颜色太暗，只修这个镜头
```

模型可能会重新生成不相关内容，导致修复不可控。

### 解决方案

后续 keyframe / video clip / render payload 都会携带：

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

这样后续模型和 render worker 都能拿到本轮修复意图。

## 6. 问题五：素材版本策略不清晰

### 问题表现

原实现对 repair callback 统一执行：

```text
currentVersion + 1
```

但不同修复目标对版本的影响不同。

### 后果

如果只是重新渲染最终视频，也提升 `currentVersion`，新版本里没有 confirmed clips，渲染会缺素材。

### 解决方案

按修复目标区分版本策略：

| 修复目标 | 是否提升 currentVersion | 原因 |
|---|---:|---|
| keyframe | 是 | 会产生新的关键帧 |
| video_clip | 是 | 会产生新的视频片段 |
| render_manifest | 否 | 只重渲染，不改变素材 |
| final_video | 否 | 只重渲染，不改变素材 |

同时，当进入新版本时：

- 未受影响的 keyframes 会复制到新版本
- 未受影响的 clips 会复制到新版本
- affected shots 会创建 `generating` 记录

这样新版本仍然是完整的，不会只剩被修复的几个镜头。

## 7. 问题六：repair event 状态语义不准确

### 问题表现

原实现中，repair workflow 回调后直接把 repair event 标记为：

```text
completed
```

但此时只是“修复计划已生成”，不代表“修复结果已完成”。

### 后果

前端和日志会误以为修复已经完成，但实际上后续生成/渲染还没结束。

### 解决方案

调整 repair event 状态语义：

```text
用户提交反馈
  -> in_progress

repair plan 回调
  -> 仍然 in_progress

keyframe / video_clip / render 成功回调
  -> completed

render 失败
  -> failed
```

## 8. 问题七：跨服务契约不一致

### 问题表现

Java 前端实际使用的 `FeedbackRequest` 包含：

```text
feedbackText
category
targetType
targetId
```

但 OpenAPI 只声明了：

```text
feedbackText
category
```

另外 Java 发送给 Python 的 repair payload 包含：

```text
repairEventId
```

但 Python `RepairRequest` schema 没有声明这个字段。

### 后果

后续重新生成 API types 或严格校验时，容易出现字段丢失或 422。

### 解决方案

同步更新：

- `docs/02-openapi-spec.yaml`
- `apps/web/src/types/api.generated.ts`
- `services/ai-orchestrator/src/schemas/workflow_requests.py`
- `services/ai-orchestrator/src/api/workflows.py`

## 9. 修改文件

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | repair callback 改为真实调度 keyframe/video_clip/render |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/RenderCallbackServiceImpl.java` | render 成功/失败后收尾 repair event |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java` | 用户提交反馈后 repair event 进入 `in_progress` |
| `services/ai-orchestrator/src/schemas/workflow_requests.py` | `RepairRequest` 增加 `repairEventId` |
| `services/ai-orchestrator/src/api/workflows.py` | repair workflow 启动时合并 `repairEventId` |
| `docs/02-openapi-spec.yaml` | `FeedbackRequest` 补充 `targetType` / `targetId` |
| `apps/web/src/types/api.generated.ts` | 同步前端 API 类型 |
| `docs/Phase/M7-Results-And-Acceptance.md` | 更新 M7 验收结论 |

## 10. 验证结果

已通过：

```text
apps/api-java:
  ./gradlew.bat compileJava

apps/web:
  npm run build

services/ai-orchestrator:
  python -m pytest tests/test_schemas.py tests/test_workflows.py tests/test_callback_payloads.py
```

Python 测试结果：

```text
50 passed
```

## 11. 后续端到端验收建议

建议手动验收以下场景：

1. `final_video` 修复
   - `/review` 提交反馈
   - 状态进入 `rendering`
   - render worker 收到新任务
   - 回调后回到 `waiting_final_review`
   - repair event 变为 `completed`

2. `video_clip` 修复
   - 选择某个镜头反馈
   - 只重生成 affected shots
   - 未受影响 clips 复制到新版本
   - 用户重新确认后进入渲染

3. `keyframe` 修复
   - 只重生成 affected shots 的 keyframe
   - 未受影响 keyframes/clips 复制到新版本
   - 用户确认后继续后续流程

## 12. 结论

M7 原实现的问题不是“没有反馈入口”，而是“反馈后没有真正执行修复”。  

本轮修复后，M7 从：

```text
反馈入口 + 状态跳转
```

升级为：

```text
反馈入口 + repair plan + 局部重生成/重渲染 + repair event 收尾
```

可以进入端到端验收。
