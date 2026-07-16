# M5 阶段结果与验收：视频片段页与 fake 生视频切片

提交日期：2026-07-16  
阶段：M5  
当前结论：代码完整就绪，需真实任务做端到端验收

## 1. M5 目标

M5 的目标是使用已确认关键帧和 fake 视频片段生成，让 `/clips` 可测试：

```text
video_clip_generating
  -> /clips 显示加载态
  -> fake callback 写入 video clips
  -> waiting_video_clip_confirmation
  -> 用户预览视频片段
  -> 用户确认/驳回片段
  -> 全部确认后进入 rendering
```

## 2. 交付物清单

### 2.1 本阶段新增/修改文件

| 文件路径 | 类型 | 改动说明 |
|---|---|---|
| `apps/api-java/.../videoclip/mapper/VideoClipMapper.java` | 修改 | `@Select` 改为 `LambdaQueryWrapper`，修复 JSONB `metadata` 列反序列化 |
| `apps/api-java/.../videoclip/service/VideoClipService.java` | 修改 | 新增 `generateClips`、`regenerateClip` 方法签名 |
| `apps/api-java/.../videoclip/service/impl/VideoClipServiceImpl.java` | 修改 | 新增 `generateClips`（批量生成）、`regenerateClip`（单片段重生成）实现，注入 `AiServiceClient`/`StoryboardMapper`/`KeyframeMapper` |
| `apps/api-java/.../videoclip/controller/VideoClipController.java` | 修改 | 新增 `POST /generate` 和 `POST /{clipId}/regenerate` 端点 |

### 2.2 已有代码（M5 依赖，无需新写）

| 层 | 文件 | 说明 |
|---|---|---|
| Java | `VideoClipEntity` | video_clips 表映射 |
| Java | `VideoClipServiceImpl` | getClips、confirmClip、rejectClip、checkAndAdvanceAfterAllConfirmed |
| Java | `AiServiceClient.startVideoClipGeneration()` | 调 Python `/ai/workflows/video-clip-generation` |
| Java | `AiCallbackServiceImpl` (case "video_clip") | 处理回调、写入 clip、消耗配额 |
| Java | `VideoTaskStateMachine` | video_clip_generating → waiting_video_clip_confirmation → rendering |
| Java | `KeyframeServiceImpl.checkAndAdvanceAfterAllConfirmed()` | 关键帧全部确认后自动触发视频片段生成 |
| Python | `FashionVideoClipWorkflow` | generate_video_clip_prompts → fake_generate_video_clips → callback_java |
| Python | `generate_video_clip_prompts.py` | 从 storyboard + keyframe 编译 clip prompt |
| Python | `fake_generate_video_clips.py` | `ENABLE_VIDEO_GENERATION=false` 时使用 fixture 数据 |
| Python | `workflows.py` POST `/workflows/video-clip-generation` | Java 触发入口 |
| Python | Fixtures: `video_clip_prompts` + `fake_video_clips` | 5 shot 的 prompt 和 clip 占位数据 |
| 前端 | `/clips/page.tsx` (226 行) | 视频片段卡片网格、确认/驳回、全部确认后进入 render |
| 前端 | Dashboard 路由 | video_clip_generating/waiting_video_clip_confirmation → /clips |
| 前端 | Review 页 | video_clip 作为 repair targetType |

## 3. 本轮发现并修复的问题

### 3.1 VideoClipMapper JSONB 反序列化

原问题：`@Select("SELECT * FROM video_clips ...")` 不走 MyBatis-Plus `autoResultMap`，`metadata` JSONB 列反序列化失败，与 TaskAssetMapper 同根因。

修复：所有 `@Select` 改为 `default` 方法 + `LambdaQueryWrapper`。

### 3.2 generate + regenerate 端点缺失

原问题：Controller 只有 GET list、confirm、reject 三个端点。M5 必需接口列表中 `POST /generate` 和 `POST /{id}/regenerate` 不存在。

修复：
- `VideoClipService` 接口新增 `generateClips`、`regenerateClip`
- `VideoClipServiceImpl` 实现：从已确认 keyframe 构建 payload，调 `aiServiceClient.startVideoClipGeneration()`
- `VideoClipController` 新增两个 `@PostMapping`

## 4. 状态流转验收

| 当前状态 | 触发动作 | 目标状态 | 实现位置 |
|---|---|---|---|
| `waiting_image_confirmation` | 全部关键帧确认 | `video_clip_generating` | KeyframeServiceImpl.checkAndAdvanceAfterAllConfirmed |
| `waiting_video_clip_confirmation` | 用户点击生成 | `video_clip_generating` | VideoClipServiceImpl.generateClips |
| `video_clip_generating` | Python callback success | `waiting_video_clip_confirmation` | AiCallbackServiceImpl (stage=video_clip) |
| `video_clip_generating` | Python callback failed | `failed` | AiCallbackServiceImpl.handleFailed |
| `waiting_video_clip_confirmation` | 单片段 regenerate | `video_clip_generating` | VideoClipServiceImpl.regenerateClip |
| `waiting_video_clip_confirmation` | 全部确认 | `rendering` | VideoClipServiceImpl.checkAndAdvanceAfterAllConfirmed |

## 5. API 端点速查

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/video-tasks/{taskId}/video-clips` | 列出视频片段 |
| POST | `/api/video-tasks/{taskId}/video-clips/generate` | 批量 AI 生成（新增）|
| POST | `/api/video-tasks/{taskId}/video-clips/{clipId}/regenerate` | 单片段重生成（新增）|
| POST | `/api/video-tasks/{taskId}/video-clips/{clipId}/confirm` | 确认 |
| POST | `/api/video-tasks/{taskId}/video-clips/{clipId}/reject` | 驳回 |

## 6. 验证结果

- Java 编译通过：`./gradlew compileJava` BUILD SUCCESSFUL
- Python 语法通过：所有活动和工作流文件 py_compile 通过
- Python fake provider：`ENABLE_VIDEO_GENERATION=false` → fixture 数据，返回 5 shot 占位 clip
- Python 测试覆盖：`test_fake_video_clips_fixture` + `test_keyframe_to_clip_flow` 端到端测试
- 前端 /clips 页面完整：视频预览、状态标签、确认/驳回/全部生成按钮

## 7. 仍需端到端验收

建议用一个有已确认关键帧的任务验证：

1. 关键帧全部确认 → 自动跳 `/clips`，状态 `video_clip_generating`
2. 等待 fake callback → 卡片出现视频预览
3. 确认一个 clip → 状态标签变为已确认
4. 驳回一个 clip → 状态变为已驳回，可点击重生成
5. 全部确认 → 出现 "Start Render" 按钮，点击进入 `rendering`

## 8. 结论

M5 核心交付物完整就绪。与 M4 类似，代码在早期阶段已预建，本轮补齐了缺失的 `generate`/`regenerate` 端点和 JSONB Mapper 修复。

当前状态：代码层缺口已修复，需真实任务端到端验证后标记为完全通过。
