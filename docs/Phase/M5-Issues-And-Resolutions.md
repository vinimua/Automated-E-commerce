# M5 问题与解决方案整理

阶段：M5 视频片段页与 fake 生视频切片  
日期：2026-07-16  
范围：Java 视频片段生成与回调、Python video clip workflow、前端 `/clips` 页面、M5 状态流转  
结论：M5 原实现“接口和页面存在”，但业务闭环不成立。本次已修复核心链路，仍建议用真实任务做端到端验收。

---

## 1. 背景

M5 的目标不是接入真实生视频模型，而是在接入真实 Provider 之前，把视频片段阶段的业务链路跑通：

```text
已确认关键帧
  -> video_clip_generating
  -> fake provider 生成视频片段
  -> Java callback 写入 video_clips
  -> waiting_video_clip_confirmation
  -> 用户逐段确认 / 驳回 / 重新生成
  -> 全部确认后进入 rendering
```

复盘后发现，M5 原实现的问题不是“没有接口”，而是基准对象、状态判断、回调写库、fake provider 和前端操作入口没有真正对齐。

M5 应该围绕一个核心基准展开：

```text
当前版本 task.currentVersion 下的 confirmed keyframes
```

也就是说，每一个当前版本已确认的关键帧，都应该对应一个当前版本的视频片段。

---

## 2. 已解决的问题

### M5-001：全部确认判断只看已有 clip，可能提前进入 rendering

严重级别：高  
影响范围：任务状态流转、渲染输入完整性、后续 render manifest  
涉及文件：

- `apps/api-java/src/main/java/com/tk/ai/video/module/videoclip/service/impl/VideoClipServiceImpl.java`

#### 问题现象

原逻辑通过统计当前版本已有 video clips 里是否存在未确认记录来决定是否进入 `rendering`：

```text
existing clips 中 unconfirmed = 0
```

这个判断有一个致命漏洞：

```text
任务应该有 4 个视频片段
数据库里实际只有 1 个 clip
这个 clip 被 confirmed
系统可能误判全部完成
```

#### 根因

系统把“已有 clips 是否都 confirmed”误当成了“所有应该存在的 clips 是否都 confirmed”。

正确基准应该是：

```text
当前版本 confirmed keyframes
```

而不是：

```text
video_clips 表里当前已有多少条记录
```

#### 解决方案

将确认完成判断改为：

```text
读取当前版本 confirmed keyframes
  -> 对每个 confirmed keyframe 检查是否存在同 shotNo 的 currentVersion clip
  -> clip 必须存在
  -> clip.status 必须是 confirmed
  -> 全部满足后才允许进入 rendering
```

#### 修复后行为

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 应有 4 个 clips，实际只有 1 个 confirmed | 可能进入 rendering | 不进入 rendering |
| 有 failed / rejected clip | 可能判断不稳定 | 不进入 rendering |
| 每个 confirmed keyframe 都有 confirmed clip | 进入 rendering | 进入 rendering |

---

### M5-002：Java 发送给 Python 的 keyframes 契约形状不对

严重级别：高  
影响范围：视频片段 prompt、fake provider、真实视频模型输入  
涉及文件：

- `VideoClipServiceImpl.java`
- `services/ai-orchestrator/src/activities/generate_video_clip_prompts.py`

#### 问题现象

Python 侧期望收到：

```json
{
  "keyframes": [
    {
      "id": "...",
      "shotNo": 1,
      "url": "...",
      "prompt": "..."
    }
  ]
}
```

但 Java 原来构造的是按 `shotNo` 做 key 的 map：

```json
{
  "1": {
    "url": "...",
    "prompt": "..."
  }
}
```

导致 Python 这段逻辑拿不到关键帧：

```python
kf_list = keyframes.get("keyframes", [])
```

#### 根因

跨服务契约没有统一。Java 以为“按 shotNo 分组的 Map”更方便，Python 却按照 `{ "keyframes": [] }` 读取。

#### 解决方案

统一 keyframes payload：

```json
{
  "version": 1,
  "targetShotNos": [1, 2, 3],
  "keyframes": [
    {
      "id": "...",
      "shotId": "...",
      "shotNo": 1,
      "imagePurpose": "first_frame",
      "url": "...",
      "prompt": "...",
      "source": "ai_generated"
    }
  ]
}
```

#### 修复后行为

Python 可以稳定读取：

```python
keyframes["keyframes"]
```

并把关键帧 URL、prompt、shotNo 传递给后续视频片段生成逻辑。

---

### M5-003：fake video provider 固定返回 fixture，和当前任务 shots 无关

严重级别：高  
影响范围：M5 验收、单片段 regenerate、批量 generate  
涉及文件：

- `services/ai-orchestrator/src/activities/generate_video_clip_prompts.py`
- `services/ai-orchestrator/src/activities/fake_generate_video_clips.py`

#### 问题现象

原 fake provider 倾向于返回固定 fixture，例如固定 6 条 clips。

这会导致：

```text
当前 storyboard 只有 3 个 shots
fake callback 却返回 6 个 clips
```

或者：

```text
用户只想 regenerate shot 2
fake provider 仍然可能返回多个 shots
```

#### 根因

fake provider 没有消费真实输入，而是直接返回静态样例。

这让 M5 表面上“有结果”，但结果无法验证当前任务链路是否正确。

#### 解决方案

改为：

```text
Java 传入 targetShotNos
  -> Python 根据 storyboard.shots 和 targetShotNos 生成 prompts
  -> fake provider 对每个 prompt 返回一个 clip
```

即：

```text
1 个 input prompt = 1 个 fake clip
```

#### 修复后行为

| 场景 | 修复前 | 修复后 |
|---|---|---|
| storyboard 3 个 shots | 可能返回固定 6 条 | 返回 3 条 |
| 单 clip regenerate | 可能影响多个 clips | 只返回目标 shot |
| 批量生成缺失片段 | fake 数据不可验证 | fake 数据与输入 shots 对齐 |

---

### M5-004：fake video URL 不保证可预览

严重级别：中  
影响范围：前端 `<video>` 预览、M5 验收体验  
涉及文件：

- `fake_generate_video_clips.py`
- `apps/web/src/app/video-tasks/[id]/clips/page.tsx`

#### 问题现象

原 fake clip URL 是外部 placeholder URL，不保证真实存在，也不保证是可播放 MP4。

这和 M5 验收要求冲突：

```text
fake video URL 能进入 <video> 预览
```

#### 根因

fake provider 返回了“看起来像 URL”的字符串，但没有保证它是浏览器可播放的视频资源。

#### 解决方案

M5 阶段先使用稳定可播放的公开 MP4 示例地址作为 fake video URL：

```text
https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4
```

同时在 URL hash 中附带 taskId / shotNo，方便排查：

```text
#task={taskId}&shot={shotNo}
```

#### 后续建议

更理想的 fake video 方案是项目内置一个本地静态 MP4，例如：

```text
apps/web/public/fake/video-clip.mp4
```

或者由 Java 提供一个内部 fake video endpoint。这样可以完全避免外部网络依赖。

---

### M5-005：video_clip callback 没有可靠处理 failed clip

严重级别：高  
影响范围：前端错误展示、重新生成、用户验收  
涉及文件：

- `AiCallbackServiceImpl.java`

#### 问题现象

原 callback 主要处理 `completed` clip。  
如果 Python 返回：

```json
{
  "shotNo": 2,
  "status": "failed",
  "errorMessage": "..."
}
```

Java 端没有稳定落库。

结果是：

```text
前端看不到 failed clip
用户无法点击重新生成
M5 的 failed 持久化验收失败
```

#### 根因

callback 只把“成功结果”当作需要持久化的数据，没有把“失败结果”也当作业务状态。

但对生成链路来说，失败也是必须持久化的结果。

#### 解决方案

callback 处理 `video_clip` 时：

```text
completed -> video_clips.status = generated
failed    -> video_clips.status = failed，并保存 errorMessage
```

同时写入：

```text
storyboardId
shotId
keyframeId
version
provider
modelName
prompt
duration
```

#### 修复后行为

失败 clip 会出现在 `/clips` 页面，并展示错误原因。用户可以点击“重新生成”。

---

### M5-006：video_clip callback 会误改旧版本数据

严重级别：高  
影响范围：多版本 repair、重新生成、渲染输入  
涉及文件：

- `VideoClipMapper.java`
- `AiCallbackServiceImpl.java`

#### 问题现象

原 callback 查找 existing clip 时只按：

```text
taskId + shotNo
```

没有限制：

```text
currentVersion
```

并且原逻辑会做：

```java
clip.setVersion(clip.getVersion() + 1)
```

这会导致 clip 的 version 和 `task.currentVersion` 脱节。

#### 根因

版本语义混乱。  
`task.currentVersion` 才是当前任务版本的权威来源，clip 不应该在 callback 里自行 `version + 1`。

#### 解决方案

新增精确查询：

```text
findByTaskIdAndShotNoAndVersion(taskId, shotNo, currentVersion)
```

callback 写入时：

```text
始终使用 task.currentVersion
不再 clip.version + 1
```

#### 修复后行为

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 重新生成当前版本 clip | 可能更新旧版本 | 只更新 currentVersion |
| callback 写入成功结果 | 可能 version 漂移 | version 与 task.currentVersion 一致 |
| 多版本 repair 后 | 数据可能混入 | 当前版本隔离 |

---

### M5-007：视频片段生成前没有预创建 generating 记录

严重级别：中高  
影响范围：前端加载态、失败排查、重新生成  
涉及文件：

- `VideoClipServiceImpl.java`

#### 问题现象

原逻辑触发 AI workflow 后，数据库里可能没有对应 clip 记录。  
如果 callback 慢、失败或丢失，前端只能看到整体 loading，无法知道每个 shot 的状态。

#### 根因

系统把“生成请求已发出”这件事完全交给 callback 结果体现，没有在 Java 业务状态源里先登记目标项。

#### 解决方案

触发生成前，Java 先为目标 shots 创建或更新：

```text
video_clips.status = generating
```

目标范围是：

```text
missing / rejected / failed clips
```

#### 修复后行为

用户刷新 `/clips` 页面时，可以看到每个正在生成的镜头卡片，而不是只有空页面或全局 loading。

---

### M5-008：前端 `/clips` 缺少 generate / regenerate 操作入口

严重级别：高  
影响范围：用户流程、M5 验收  
涉及文件：

- `apps/web/src/app/video-tasks/[id]/clips/page.tsx`

#### 问题现象

后端虽然有：

```http
POST /api/video-tasks/{taskId}/video-clips/generate
POST /api/video-tasks/{taskId}/video-clips/{clipId}/regenerate
```

但前端页面没有完整入口。

结果：

```text
用户无法主动生成缺失视频片段
用户无法对 failed / rejected clip 重新生成
```

#### 根因

前端只做了展示、确认、驳回、渲染，没有把 M5 必需的生成/重生成动作接上。

#### 解决方案

`/clips` 页面新增：

- “生成视频片段”按钮；
- “生成缺失/失败片段”按钮；
- failed / rejected clip 卡片上的“重新生成”按钮；
- failed clip 的错误信息展示；
- 生成中状态展示。

#### 修复后行为

用户可以完成：

```text
生成 clips
  -> 预览
  -> 确认 / 驳回
  -> 对驳回或失败项重新生成
  -> 全部确认后进入渲染
```

---

### M5-009：状态机不允许从确认页回到生成中

严重级别：高  
影响范围：regenerate clip  
涉及文件：

- `VideoTaskStateMachine.java`

#### 问题现象

M5 要求允许：

```text
waiting_video_clip_confirmation -> video_clip_generating
```

因为用户可能在确认页驳回某个 clip，然后重新生成它。

但状态机原来不允许这个跳转。

#### 根因

状态机只覆盖了首次生成流程，没有覆盖确认页内的局部重生成流程。

#### 解决方案

补充状态流转：

```text
waiting_video_clip_confirmation -> video_clip_generating
```

#### 修复后行为

用户在 `/clips` 页面点击“重新生成”时，任务可以合法回到：

```text
video_clip_generating
```

callback 后再回到：

```text
waiting_video_clip_confirmation
```

---

## 3. 修复后的关键流程

### 3.1 批量生成视频片段

```text
用户点击生成视频片段
  -> Java 读取当前版本 confirmed keyframes
  -> Java 读取当前版本已有 video clips
  -> 找出 missing / rejected / failed clips
  -> 为目标 shots 写入 generating clip
  -> Java 构造 storyboard + keyframes payload
  -> Java 调用 Python /ai/workflows/video-clip-generation
  -> Python 根据 targetShotNos 生成 prompts
  -> fake provider 每个 prompt 返回一个 clip
  -> Python callback Java stage=video_clip
  -> Java 写入 generated / failed clips
  -> 任务回到 waiting_video_clip_confirmation
```

### 3.2 单个视频片段重新生成

```text
用户驳回 / 某 clip 失败
  -> clip.status = rejected / failed
  -> 用户点击重新生成
  -> Java 将该 clip 置为 generating
  -> task.status = video_clip_generating
  -> Java 只发送该 shot 的 storyboard/keyframe payload
  -> Python 只返回该 shot 的 fake clip
  -> callback 只更新当前版本该 shot 的 clip
```

### 3.3 全部确认后进入渲染

```text
用户确认一个 clip
  -> clip.status = confirmed
  -> Java 检查当前版本所有 confirmed keyframes
  -> 每个 confirmed keyframe 必须有 confirmed clip
  -> 如果还有缺失 / 未确认 / failed / rejected，停留在 waiting_video_clip_confirmation
  -> 全部满足后进入 rendering
```

---

## 4. 修改文件清单

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/state/VideoTaskStateMachine.java` | 允许 `waiting_video_clip_confirmation -> video_clip_generating` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videoclip/mapper/VideoClipMapper.java` | 增加 currentVersion + shotNo 精确查询 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videoclip/service/impl/VideoClipServiceImpl.java` | 重写 M5 clip 生成、重生成、确认完整性判断 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | 修复 video_clip callback 写库、失败落库、版本漂移、关联字段 |
| `services/ai-orchestrator/src/activities/generate_video_clip_prompts.py` | 按真实 storyboard/keyframes 生成 prompts，支持 `targetShotNos` |
| `services/ai-orchestrator/src/activities/fake_generate_video_clips.py` | fake provider 改为按输入 prompts 返回 clips |
| `apps/web/src/app/video-tasks/[id]/clips/page.tsx` | 增加生成、重新生成、错误展示和正常中文文案 |

---

## 5. 验证结果

已执行：

```text
python -m py_compile services/ai-orchestrator/src/activities/generate_video_clip_prompts.py services/ai-orchestrator/src/activities/fake_generate_video_clips.py
```

结果：通过。

```text
./gradlew.bat compileJava
```

结果：通过。

说明：第一次在沙箱内执行 Gradle 时遇到 `.gradle` lock 权限问题，授权后重新执行通过。

```text
npm run build
```

结果：通过。

---

## 6. 仍建议做的端到端验收

建议用一个包含 3 个 storyboard shots 的真实任务验证：

1. 完成 storyboard 并进入 keyframes。
2. 为 3 个 shots 生成并确认关键帧。
3. 进入 `/clips`。
4. 点击“生成视频片段”。
5. 确认 fake callback 后页面出现 3 个 clips。
6. 驳回其中一个 clip。
7. 点击该 clip 的“重新生成”。
8. 确认只更新该 clip，不影响其它 clips。
9. 将 3 个 clips 全部确认。
10. 验证任务进入 `rendering`。

---

## 7. 当前结论

M5 的主要问题已经从代码层面修复：

```text
confirmed keyframes
  -> clips generation
  -> fake provider
  -> callback persistence
  -> frontend confirmation/regeneration
  -> rendering transition
```

现在 M5 不再只是“有接口、有页面”，而是具备可验收的业务闭环。

不过在没有跑完整真实任务之前，M5 状态仍建议标记为：

```text
关键问题已修复，待端到端验收。
```
