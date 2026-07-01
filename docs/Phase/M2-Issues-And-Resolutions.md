# M2 问题与解决方案统计文档

> 本文记录本次对 `docs/Phase/M2-Results-And-Acceptance.md` 进行复验后发现的问题、问题原因、修复方案、涉及文件与验证结果。  
> 阶段：M2 Java 状态机和 API  
> 日期：2026-06-27

---

## 1. 总体结论

M2 原验收文档中写的是“已通过”，但复验代码后发现：Java 后端虽然已经补了大量 Fashion Creative Loop 的实体、接口和状态机，但主流程中仍存在几个会导致链路卡住的问题。

本次已经修复这些主流程问题。修复后，M2 可以调整为：

```text
修复后有条件通过
```

这里的“有条件”是指：

1. Java 后端的状态机、API、回调处理和契约字段已经修复。
2. Python Fashion workflow 仍属于后续 M4 范围，目前 Java 只负责发起 fire-and-forget 调度。
3. RenderManifest 构建与 RabbitMQ 推送仍属于后续渲染链路范围，当前只补了渲染前置校验。

---

## 2. 问题与解决方案

| 编号 | 问题 | 严重级别 | 问题原因 | 解决方案 |
|---|---|---|---|---|
| M2-001 | 素材确认后主流程会卡住 | 高 | Fashion 新任务创建后状态是 `asset_uploading`，但 `/assets/confirm` 原逻辑直接尝试进入 `waiting_asset_confirmation`，状态机不允许这个跳转 | 将素材确认拆成两段：`asset_uploading -> asset_analyzing` 触发 AI 素材分析；`waiting_asset_confirmation -> reference_analyzing/plan_generating` 进入后续创意流程 |
| M2-002 | 确认方案时无法选择具体方案 | 高 | `/confirm-plan` 没有请求体，无法传入 `planId`，后端也没有保存 `selectedPlanId` | 新增 `ConfirmPlanRequest(planId)`，确认方案时校验方案归属、写入 `selectedPlanId`，并触发分镜生成 |
| M2-003 | `creative_plan` 回调字段和契约不一致 | 高 | OpenAPI / AI Schema 使用 `plans`，Java 回调处理却读取 `creativePlan` | Java 回调 DTO 和 handler 改为读取 `plans`，移除非契约字段 `creativePlan` |
| M2-004 | 用户上传的关键帧 / 视频片段被当成已确认 | 高 | mapper 统计未确认数量时排除了 `uploaded`，导致 `uploaded` 和 `confirmed` 语义混在一起 | 未确认统计只排除 `confirmed`，`uploaded` 必须等待用户显式确认 |
| M2-005 | 没有确认视频片段也可能进入渲染 | 中 | `requestRender()` 只检查未确认数量，没有确保当前版本至少存在一个已确认 clip | 渲染前必须存在至少一个当前版本 `confirmed` video clip，并且当前版本 clip 全部确认 |

---

## 3. 关键状态说明

### 3.1 素材确认链路

修复前：

```text
asset_uploading
  -> /assets/confirm
  -> waiting_asset_confirmation
  -> 状态机不允许，流程卡住
```

修复后：

```text
asset_uploading
  -> 用户确认已上传素材
  -> asset_analyzing
  -> AI 素材分析回调
  -> waiting_asset_confirmation
  -> 用户确认分析结果
  -> reference_analyzing 或 plan_generating
```

### 3.2 方案确认链路

修复前：

```text
waiting_plan_selection
  -> /confirm-plan
  -> 不知道用户选了哪个 plan
  -> selectedPlanId 为空
  -> 后续分镜生成缺少输入
```

修复后：

```text
waiting_plan_selection
  -> /confirm-plan { planId }
  -> 保存 selectedPlanId
  -> storyboard_generating
  -> 调度 AI 分镜生成
```

### 3.3 `uploaded` 和 `confirmed` 的区别

| 状态 | 含义 |
|---|---|
| `uploaded` | 用户上传了图片或视频片段，但还没有确认可用 |
| `generated` | AI 生成了图片或视频片段，但还没有确认可用 |
| `confirmed` | 用户明确确认该素材可用于后续流程 |
| `rejected` | 用户驳回，需要重生成、重传或修复 |

所以 `uploaded` 不能等于 `confirmed`。  
否则用户只要上传图片，系统就会自动往后走，失去人工审核节点。

---

## 4. 修改文件清单

### 4.1 Java 后端

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/dto/ConfirmPlanRequest.java` | 新增确认方案请求 DTO，包含 `planId` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/controller/VideoTaskController.java` | `/confirm-plan` 改为接收请求体 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/VideoTaskService.java` | `confirmPlan` 方法签名增加 `ConfirmPlanRequest` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java` | 修复方案确认、分镜调度、渲染前 clip 校验 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/taskasset/service/impl/TaskAssetServiceImpl.java` | 修复素材确认的两阶段状态流转 |
| `apps/api-java/src/main/java/com/tk/ai/video/common/AiServiceClient.java` | 新增 `startAssetAnalysis` 和 `startStoryboardGeneration` 调度方法 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/dto/AiCallbackRequest.java` | 移除非契约字段 `creativePlan`，使用 `plans` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | `creative_plan` 回调改为消费 `plans` 字段 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/mapper/KeyframeMapper.java` | 未确认统计只排除 `confirmed` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videoclip/mapper/VideoClipMapper.java` | 未确认统计只排除 `confirmed`，并新增按版本查询 clip |

### 4.2 契约与前端类型

| 文件 | 修改内容 |
|---|---|
| `docs/02-openapi-spec.yaml` | 新增 `ConfirmPlanRequest`，并给 `/confirm-plan` 补充请求体 |
| `apps/web/src/types/api.generated.ts` | 根据 OpenAPI 重新生成前端 API 类型 |
| `apps/web/src/schemas/video-task.ts` | 新增 `ConfirmPlanSchema` |

### 4.3 测试

| 文件 | 覆盖内容 |
|---|---|
| `apps/api-java/src/test/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImplTest.java` | 验证确认方案会保存 `selectedPlanId` 并触发分镜生成 |
| `apps/api-java/src/test/java/com/tk/ai/video/module/taskasset/service/impl/TaskAssetServiceImplTest.java` | 验证 `asset_uploading` 下确认素材会进入 `asset_analyzing` 并触发素材分析 |

---

## 5. 修复后的主流程

```text
1. 创建 Fashion 任务
   draft -> asset_uploading

2. 用户上传素材并确认
   asset_uploading -> asset_analyzing

3. AI 素材分析完成
   asset_analyzing -> waiting_asset_confirmation

4. 用户确认素材分析结果
   waiting_asset_confirmation -> reference_analyzing 或 plan_generating

5. AI 生成创意方案
   plan_generating -> waiting_plan_selection

6. 用户选择具体方案
   waiting_plan_selection -> storyboard_generating

7. AI 生成分镜
   storyboard_generating -> waiting_storyboard_confirmation

8. 用户确认分镜
   waiting_storyboard_confirmation -> keyframe_configuring

9. 用户上传或 AI 生成关键帧
   keyframe_configuring/image_generating -> waiting_image_confirmation

10. 用户确认所有关键帧
    waiting_image_confirmation -> video_clip_generating

11. AI 生成视频片段
    video_clip_generating -> waiting_video_clip_confirmation

12. 用户确认所有视频片段
    waiting_video_clip_confirmation -> rendering
```

---

## 6. 验证结果

| 验证命令 | 结果 |
|---|---|
| `apps/api-java/.\\gradlew.bat test` | 通过 |
| `scripts/check-contract-sync.ps1` | 通过 |
| `apps/web/npm run generate:api-types` | 通过 |
| `apps/web/npm run build` | 通过 |
| `apps/web/npm run type-check` | 通过 |

注意：`npm run type-check` 不建议和 `npm run build` 并行运行。  
原因是 Next.js build 会生成 / 清理 `.next/types`，并行执行时可能产生临时竞态，导致 type-check 误报。

---

## 7. 仍需后续阶段处理的问题

| 问题 | 所属阶段 | 说明 |
|---|---|---|
| Python Fashion workflow 还未完整实现 | M4 | Java 已经能发起调度，但 Python 侧还需要实现对应 workflow endpoint |
| RenderManifest 构建和 RabbitMQ 推送还未接入当前 render 请求 | M7 | 本次只修复渲染前置校验，真正渲染链路仍在后续阶段 |
| 前端 Fashion 工作台页面还未完成 | M3 | 后端接口已补齐，前端 assets/keyframes/clips/review 页面仍需实现 |
| 更多专项单元测试仍可补强 | M3+ | 当前补了关键断点测试，后续可以继续覆盖所有非法状态、权限和额度场景 |

---

## 8. 给后续 AI 开发的提醒

后续继续开发 M3/M4/M7 时，必须优先读取并对齐以下契约：

1. `docs/01-database-schema.sql`
2. `docs/02-openapi-spec.yaml`
3. `docs/03-ai-output-json-schema.md`
4. `docs/04-render-manifest-schema.md`
5. `docs/Phase/M1.5-Typed-Contract-Runtime-Validation.md`
6. `docs/Phase/M2-Issues-And-Resolutions.md`

同时建议每次跨服务修改后运行：

```powershell
.\scripts\check-contract-sync.ps1
```

这样可以避免 DB、OpenAPI、Python Pydantic、前端类型、RenderManifest 之间再次发生字段或枚举漂移。
