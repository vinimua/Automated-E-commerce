# M4 阶段结果与验收：关键帧页与 fake 生图切片

提交日期：2026-07-16  
阶段：M4  
当前结论：主流程已补齐关键缺口，需用真实任务再做端到端验收

## 1. M4 目标

M4 的目标是在接入真实关键帧图片 Provider 前，让 `/keyframes` 页面可以完整跑通：

```text
keyframe_configuring
  -> 用户上传关键帧或请求 AI 生成
  -> image_generating
  -> fake callback 写入 keyframes
  -> waiting_image_confirmation
  -> 用户逐帧确认/驳回
  -> 全部 storyboard shots 都确认后进入 video_clip_generating
```

## 2. 本轮发现并修复的问题

### 2.1 全部确认判断不完整

原问题：

后端只统计已存在 keyframe 中 `status != confirmed` 的数量。如果 storyboard 有 6 个 shots，但数据库里只存在 1 条已确认 keyframe，也可能被误判为“全部确认”。

修复：

- 以 storyboard shots 为基准。
- 每个 shot 都必须有当前版本 keyframe。
- 每个 keyframe 都必须是 `confirmed`。
- 缺任意 shot 或存在未确认 keyframe 时，不允许进入 `video_clip_generating`。

涉及文件：

- `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/service/impl/KeyframeServiceImpl.java`

### 2.2 批量 generate 没有处理 rejected / failed

原问题：

批量生成只处理“没有 keyframe 记录”的 shot。已存在 `rejected` / `failed` 的 shot 会被跳过，和 M4 验收规则不一致。

修复：

- 批量生成会处理：
  - missing
  - draft
  - rejected
  - failed
- 会跳过：
  - uploaded
  - generated
  - confirmed
  - generating

### 2.3 同一 shot 可能产生重复 keyframe

原问题：

用户重新上传或重新请求 AI 生成同一个 shot 时，可能插入重复 keyframe。前端按 `shotNo` 查第一条，容易显示旧记录。

修复：

- 同一 `taskId + shotNo + currentVersion` 优先更新已有 keyframe。
- 已确认 keyframe 不允许被直接覆盖。

### 2.4 keyframe 缺少 storyboard shot 关联

原问题：

`KeyframeEntity.shotId` 没有从 storyboard shot 填充，后续 M5 视频片段、repair、render manifest 难以稳定追踪。

修复：

- 创建用户上传 keyframe 时写入 `storyboardId` 和 `shotId`。
- 批量生成 keyframe draft/generating 记录时写入 `storyboardId` 和 `shotId`。
- AI keyframe callback 写入结果时补齐 `storyboardId` 和 `shotId`。

涉及文件：

- `KeyframeServiceImpl.java`
- `AiCallbackServiceImpl.java`

### 2.5 fake 图片 URL 不可预览

原问题：

Fake provider 返回 placeholder COS URL，前端可能显示 broken image，不满足“callback 后能看到图片预览”。

修复：

- `FakeImageProvider` 改为返回确定性的 SVG `data:image/svg+xml;base64,...`。
- 不依赖外部域名。
- 前端 `<img>` 可以直接预览。

涉及文件：

- `services/ai-orchestrator/src/providers/fake_image.py`

## 3. 状态流转规则

允许状态：

```text
keyframe_configuring -> image_generating
keyframe_configuring -> waiting_image_confirmation
image_generating -> waiting_image_confirmation
waiting_image_confirmation -> image_generating
waiting_image_confirmation -> video_clip_generating
```

重要约束：

- 用户上传关键帧不会调用图片 Provider。
- AI 批量生成只生成 missing/draft/rejected/failed 的 shots。
- 单帧 regenerate 只影响一个 keyframe。
- 只有所有 storyboard shots 都有 confirmed keyframe，才允许进入 `video_clip_generating`。

## 4. 验证结果

已完成：

- Python fake provider 语法检查通过：

```text
python -m py_compile services/ai-orchestrator/src/providers/fake_image.py services/ai-orchestrator/src/activities/fake_generate_keyframes.py
```

- Java 编译通过：

```text
./gradlew.bat compileJava
```

## 5. 仍需端到端验收

建议用一个包含 3 个 storyboard shots 的任务验证：

1. 进入 `/keyframes`，应看到 3 张 shot 卡片。
2. 只上传并确认 shot 1，任务不应进入 `video_clip_generating`。
3. 批量 AI 生成其余 missing shots。
4. fake callback 后应看到可预览图片，而不是 broken image。
5. reject 一个 keyframe 后，批量 generate 应重新处理该 shot。
6. failed keyframe 也应可被批量 generate 重新处理。
7. 三个 keyframes 全部 confirmed 后，任务才进入 `video_clip_generating`。

## 6. 结论

M4 原先的主要问题不是接口缺失，而是“验收判断过松”。本轮已修复关键数据完整性和状态推进问题。

当前状态：

```text
代码层关键缺口已修复；
还需要跑一条真实任务做端到端验收后，才能把 M4 标记为完全通过。
```
