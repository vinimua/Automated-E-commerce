# M3 问题与解决方案统计文档

> 本文记录本次对 `docs/Phase/M3-Results-And-Acceptance.md` 的复验结论、发现的问题、修复方案、涉及文件与验证结果。  
> 阶段：M3 前端工作流壳子  
> 日期：2026-06-29

---

## 1. 总体结论

M3 原验收文档将阶段结论写为“已通过”。复验时发现，前端页面确实已经创建完成，并且 TypeScript 与生产构建可以通过，但存在多个主流程断点：

1. 方案选择页面仍调用旧接口，绕过 M2 新链路。
2. 素材分析完成后的二次确认无法继续推进。
3. 关键帧页面把 `uploaded` 错误显示为“已确认”。
4. “AI 生成关键帧”只创建草稿，没有触发 AI 调度。
5. 成片审核“批准”只是跳转，没有改变任务状态。

本次已完成修复。修复后，M3 可以调整为：

```text
修复后通过，剩余真实 AI 产出能力依赖 M4 Python Fashion Workflow。
```

---

## 2. 问题与解决方案

| 编号 | 问题 | 严重级别 | 问题原因 | 解决方案 |
|---|---|---|---|---|
| M3-001 | 方案页仍调用旧接口 `/select-plan` | 高 | M2 新增了 Fashion 链路的 `/confirm-plan`，但 M3 页面仍复用旧 V1 方案选择接口 | 前端改为调用 `/api/video-tasks/{taskId}/confirm-plan`，选择后进入 `storyboard_generating` |
| M3-002 | `waiting_asset_confirmation` 阶段无法继续确认 | 高 | 前端确认按钮只在 `asset_uploading` 显示；后端也错误尝试 `waiting_asset_confirmation -> waiting_asset_confirmation` | 前端允许 `waiting_asset_confirmation` 继续确认；后端直接从当前状态流转到 `reference_analyzing` 或 `plan_generating` |
| M3-003 | 关键帧 `uploaded` 被显示为“已确认” | 高 | 页面将 `uploaded` 和 `confirmed` 混为一类，违背 M2 “用户必须显式确认”的设计 | 前端只将 `confirmed` 显示为已确认；`uploaded/generated` 显示为待确认 |
| M3-004 | AI 生成关键帧没有真正触发 AI 调度 | 高 | 前端提交 `source=ai_generated` 后，后端只插入 `draft` 关键帧记录，没有推进任务状态或调用 AI | 后端在 `ai_generated` 时推进到 `image_generating`，并调用 `AiServiceClient.startKeyframeGeneration` |
| M3-005 | 成片审核没有真正批准任务 | 高 | Review 页“批准成片”只是跳转到视频详情页，没有调用后端状态流转 | 新增 `/api/video-tasks/{taskId}/approve`，将 `waiting_final_review -> completed`，前端按钮改为调用该接口 |
| M3-006 | 最后一个视频片段确认后页面不自动进入审核页 | 中 | 后端确认最后一个 clip 后会进入 `rendering`，但前端只刷新列表 | 当前端收到 `rendering` 状态时自动跳转 `/review` |
| M3-007 | 构建存在 `img alt` warning | 低 | lucide 的 `Image` 图标被 ESLint 误判成 HTML `img` | 将图标 import 改名为 `ImageIcon`，构建 warning 已清除 |

---

## 3. 修复后的关键流程

### 3.1 方案确认流程

修复前：

```text
waiting_plan_selection
  -> 前端调用 /select-plan
  -> script_generating
  -> 进入旧 V1 脚本/素材链路
```

修复后：

```text
waiting_plan_selection
  -> 前端调用 /confirm-plan
  -> Java 保存 selectedPlanId
  -> storyboard_generating
  -> 调度 storyboard-generation workflow
```

### 3.2 素材确认流程

修复前：

```text
asset_uploading
  -> 用户确认素材
  -> asset_analyzing
  -> AI 回调 waiting_asset_confirmation
  -> 前端没有可用确认按钮，流程停住
```

修复后：

```text
asset_uploading
  -> 用户确认素材
  -> asset_analyzing
  -> AI 回调 waiting_asset_confirmation
  -> 用户确认素材分析结果
  -> reference_analyzing 或 plan_generating
```

### 3.3 关键帧确认流程

修复前：

```text
uploaded
  -> 前端显示“已确认”
  -> 用户可能误以为可以进入视频片段阶段
```

修复后：

```text
uploaded/generated
  -> 前端显示“待确认”
  -> 用户点击确认
  -> confirmed
  -> 所有关键帧 confirmed 后进入 video_clip_generating
```

### 3.4 成片审核流程

修复前：

```text
waiting_final_review
  -> 点击“批准成片”
  -> 跳转 /videos/{videoId}
  -> video_tasks.status 仍未变为 completed
```

修复后：

```text
waiting_final_review
  -> POST /api/video-tasks/{taskId}/approve
  -> completed
  -> 跳转视频详情页
```

---

## 4. 修改文件清单

### 4.1 Java 后端

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/taskasset/service/impl/TaskAssetServiceImpl.java` | 修复 `waiting_asset_confirmation` 二次确认状态流转 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/service/impl/KeyframeServiceImpl.java` | `ai_generated` 关键帧请求推进到 `image_generating`，并触发 `startKeyframeGeneration` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/VideoTaskService.java` | 新增 `approveFinalReview` 方法 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/controller/VideoTaskController.java` | 新增 `POST /api/video-tasks/{taskId}/approve` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java` | 实现 `waiting_final_review -> completed` 的批准逻辑 |

### 4.2 OpenAPI 与前端类型

| 文件 | 修改内容 |
|---|---|
| `docs/02-openapi-spec.yaml` | 新增 `/api/video-tasks/{taskId}/approve` 契约 |
| `apps/web/src/types/api.generated.ts` | 根据 OpenAPI 重新生成 |

### 4.3 前端页面

| 文件 | 修改内容 |
|---|---|
| `apps/web/src/app/video-tasks/[id]/plans/page.tsx` | `/select-plan` 改为 `/confirm-plan`，选择后跳转进度页 |
| `apps/web/src/app/video-tasks/[id]/assets/page.tsx` | `waiting_asset_confirmation` 阶段允许确认并根据返回状态跳转 |
| `apps/web/src/app/video-tasks/[id]/keyframes/page.tsx` | `uploaded` 不再显示为已确认，只显示为待确认 |
| `apps/web/src/app/video-tasks/[id]/clips/page.tsx` | 最后一个片段确认进入 `rendering` 后自动跳转审核页；`uploaded` 图标改为待确认 |
| `apps/web/src/app/video-tasks/[id]/review/page.tsx` | “批准成片”改为调用 `/approve` 接口 |

---

## 5. 修复后的 M3 用户主流程

```text
1. Dashboard 点击任务
   -> 根据 status 进入对应页面

2. 素材上传页
   asset_uploading -> asset_analyzing

3. 素材分析确认
   waiting_asset_confirmation -> reference_analyzing / plan_generating

4. 方案选择页
   waiting_plan_selection -> storyboard_generating

5. 分镜确认页
   waiting_storyboard_confirmation -> keyframe_configuring

6. 关键帧页
   uploaded/generated -> confirmed
   all confirmed -> video_clip_generating

7. 视频片段页
   uploaded/generated -> confirmed
   all confirmed -> rendering

8. 成片审核页
   waiting_final_review -> approve -> completed
   waiting_final_review -> feedback -> repairing
```

---

## 6. 验证结果

| 验证命令 | 结果 |
|---|---|
| `apps/api-java/.\\gradlew.bat test` | 通过 |
| `apps/web/npm run generate:api-types` | 通过 |
| `apps/web/npm run type-check` | 通过 |
| `apps/web/npm run build` | 通过，无 warning |
| `scripts/check-contract-sync.ps1` | 通过 |

---

## 7. 剩余边界与后续阶段

| 项目 | 所属阶段 | 说明 |
|---|---|---|
| Python Fashion workflow 真实实现 | M4 | Java 已经能调度 asset/storyboard/keyframe 等 workflow，但 Python 侧仍需实现对应 endpoint 和 Temporal workflow |
| AI 关键帧真实生成 | M4/M5 | 当前 Java 会进入 `image_generating` 并发起调度，真正产图依赖 Python provider |
| 视频片段真实生成 | M4/M6 | 前端和 Java 确认链路已准备好，真实片段生成仍依赖后续 AI workflow |
| RenderManifest 构建与 RabbitMQ 渲染推送 | M7 | 当前 M3 只覆盖前端工作流壳子与状态推进，不负责最终渲染链路 |
| M3 原验收文档乱码 | 文档修复 | `M3-Results-And-Acceptance.md` 仍存在编码乱码，建议后续按本文件内容重写一版清晰验收文档 |

---

## 8. 给后续 AI 开发的提醒

后续继续开发 M4/M5/M6/M7 时，必须优先对齐：

1. `docs/01-database-schema.sql`
2. `docs/02-openapi-spec.yaml`
3. `docs/03-ai-output-json-schema.md`
4. `docs/04-render-manifest-schema.md`
5. `docs/Phase/M1.5-Typed-Contract-Runtime-Validation.md`
6. `docs/Phase/M2-Issues-And-Resolutions.md`
7. `docs/Phase/M3-Issues-And-Resolutions.md`

每次跨服务修改后建议运行：

```powershell
.\scripts\check-contract-sync.ps1
```

这能防止 DB、OpenAPI、Python Pydantic、前端类型和 RenderManifest 再次发生字段或枚举漂移。
