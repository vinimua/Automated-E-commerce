# M4 问题与解决方案统计文档

> 阶段：M4 Python fake provider 工作流  
> 来源：对 `docs/Phase/M4-Results-And-Acceptance.md` 的复验，以及对 Java 主后端、Python AI Orchestrator、AI 输出契约的交叉检查。  
> 结论：M4 的 Python fake provider、Pydantic Schema、Activity 级测试是可用的；本次主要修复的是 Java 主链路与 Python M4 workflow 之间的触发接线、状态流转和 callback 契约不一致问题。

---

## 1. 总体结论

M4 原始验收文档把阶段结论写为“已通过”。复验后发现，Python 侧 workflow 和 fake provider 的确已经具备基础能力，但存在 4 个会影响真实主链路的问题：

1. 关键帧全部确认后，Java 没有触发 Python 视频片段生成 workflow。
2. 用户提交修复反馈后，Java 没有触发 Python repair workflow。
3. repair 成功回调会落到 `repairing -> repairing` 这种非法自跳，导致状态无法继续推进。
4. `reference_analysis` 的 Python 回调状态与 Java 状态机不一致。

这些问题已完成修复。修复后 M4 可以调整为：

```text
修复后通过：Python fake provider 工作流可用，Java 主链路已能触发 video_clip / repair workflow；
真实 Temporal Server 端到端运行仍保留到 M11 验证。
```

---

## 2. 问题与解决方案

| 编号 | 问题 | 严重级别 | 问题原因 | 解决方案 |
|---|---|---|---|---|
| M4-001 | 关键帧全部确认后没有触发视频片段生成 | 高 | `KeyframeServiceImpl` 只把任务状态推进到 `video_clip_generating`，但没有调用 `AiServiceClient.startVideoClipGeneration()` | 在所有关键帧确认后调用 `startVideoClipGeneration()`，并把已确认关键帧整理成 Python 需要的 `keyframes` payload |
| M4-002 | `startVideoClipGeneration()` 请求体与 Python Schema 不一致 | 高 | Java 原来发送 `params`，但 Python `VideoClipGenerationRequest` 要求 `storyboard` 和 `keyframes` | 修改 Java client 方法签名和请求体，改为发送 `storyboard`、`keyframes` |
| M4-003 | 用户提交反馈后没有触发 repair workflow | 高 | `VideoTaskServiceImpl.submitFeedback()` 只创建 `repair_event` 并设置任务状态为 `repairing`，没有调用 Python | 在 `submitFeedback()` 中调用 `AiServiceClient.startRepairWorkflow()`，并传入 `feedbackText`、`category`、`targetType`、`currentState` |
| M4-004 | `startRepairWorkflow()` 请求体与 Python Schema 不一致 | 高 | Java 原来只发送 `repairEventId` 和 `targetType`，但 Python `RepairRequest` 要求 `feedbackText` 和 `category` | 修改 Java client，补齐 Python request schema 需要的字段 |
| M4-005 | repair 成功回调会产生非法状态自跳 | 高 | Python repair 成功后回调 `nextTaskStatus=repairing`；Java callback 又把 repair retry target 解析为 `repairing`，导致 `repairing -> repairing` | repair callback 改为根据 `targetType` 跳到局部重生成状态，例如 `video_clip -> video_clip_generating`、`keyframe -> image_generating` |
| M4-006 | `reference_analysis` 回调状态与 Java 状态机不一致 | 中 | Python reference workflow 成功后回调 `waiting_asset_confirmation`，但 Java `reference_analysis` handler 实际推进到 `plan_generating` | Python workflow 和 AI 输出契约统一改为 `reference_analysis -> plan_generating` |
| M4-007 | repair callback 无法稳定回写 `repair_events` | 中 | Java callback 原来尝试从 `targetId` 找 repair event，但 repair 的目标对象和 repair event 是两件事 | 在 `RepairResult` 中增加可选 `repairEventId`，Python repair workflow 回调时带回，Java callback 优先用它更新 repair event |
| M4-008 | M4 验收文档仍把已修复问题描述为“未接线” | 低 | 验收文档中的风险项和未完成项没有随代码修复同步更新 | 更新 `M4-Results-And-Acceptance.md` 中 reference / repair 的状态描述和 Java 接线状态 |

---

## 3. 修复后的关键链路

### 3.1 关键帧到视频片段

修复前：

```text
用户确认全部关键帧
  -> Java 设置 video_tasks.status = video_clip_generating
  -> 没有触发 Python
  -> 任务停在 video_clip_generating
```

修复后：

```text
用户确认全部关键帧
  -> Java 设置 video_tasks.status = video_clip_generating
  -> Java 调用 /ai/workflows/video-clip-generation
  -> Python 执行 FashionVideoClipWorkflow
  -> Python 回调 Java stage=video_clip
  -> Java 推进到 waiting_video_clip_confirmation
```

### 3.2 用户反馈到局部修复

修复前：

```text
用户提交反馈
  -> Java 创建 repair_event
  -> Java 设置 video_tasks.status = repairing
  -> 没有触发 Python repair workflow
```

修复后：

```text
用户提交反馈
  -> Java 创建 repair_event
  -> Java 设置 video_tasks.status = repairing
  -> Java 调用 /ai/workflows/repair
  -> Python 执行 FashionRepairWorkflow
  -> Python 回调 Java stage=repair
  -> Java 根据 targetType 进入局部重生成状态
```

当前映射关系：

| targetType | repair 成功后的目标状态 |
|---|---|
| `keyframe` | `image_generating` |
| `video_clip` | `video_clip_generating` |
| `render_manifest` | `rendering` |
| `final_video` | `rendering` |
| 其他 / 缺省 | `keyframe_configuring` |

### 3.3 参考视频分析

修复前：

```text
reference_analyzing
  -> Python callback nextTaskStatus=waiting_asset_confirmation
  -> Java 实际 handler 推进到 plan_generating
  -> Python / Java / 文档语义不一致
```

修复后：

```text
reference_analyzing
  -> Python callback nextTaskStatus=plan_generating
  -> Java handler 推进到 plan_generating
  -> 契约、代码、状态机一致
```

---

## 4. 修改文件清单

### 4.1 Java 后端

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/common/AiServiceClient.java` | 修改 `startVideoClipGeneration()` 请求体为 `storyboard/keyframes`；修改 `startRepairWorkflow()` 请求体，补齐 `feedbackText/category/currentState` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/service/impl/KeyframeServiceImpl.java` | 所有关键帧确认后触发 `startVideoClipGeneration()`；修复 `waiting_image_confirmation -> waiting_image_confirmation` 自跳问题 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java` | `submitFeedback()` 创建 repair event 后触发 `startRepairWorkflow()` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | repair callback 根据 `targetType` 进入目标重生成状态；优先使用 `repairEventId` 回写 repair event |

### 4.2 Python AI Orchestrator

| 文件 | 修改内容 |
|---|---|
| `services/ai-orchestrator/src/workflows/fashion_analysis.py` | `reference_analysis` 成功回调改为 `plan_generating` |
| `services/ai-orchestrator/src/workflows/fashion_repair.py` | repair 成功回调改为目标类型对应状态；回传 `repairEventId` |
| `services/ai-orchestrator/src/schemas/workflow_requests.py` | `VideoClipGenerationRequest.keyframes` 改为与 workflow 消费一致的 dict 结构 |
| `services/ai-orchestrator/src/schemas/ai_outputs.py` | `RepairResult` 增加可选 `repairEventId` |
| `services/ai-orchestrator/tests/test_callback_payloads.py` | 更新 reference / repair callback 预期状态 |
| `services/ai-orchestrator/tests/test_workflows.py` | 更新 repair workflow 模拟链路，覆盖 `repairEventId` 和 `video_clip_generating` |

### 4.3 契约与验收文档

| 文件 | 修改内容 |
|---|---|
| `docs/03-ai-output-json-schema.md` | `RepairResult` 增加 `repairEventId`；callback stage contract 中 `reference_analysis` 改为 `plan_generating`；`repair` 改为目标类型状态 |
| `docs/Phase/M4-Results-And-Acceptance.md` | 更新 M4 验收结论中的 Java 接线状态、reference 状态和 repair 状态 |

---

## 5. 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| Python 单测 | `python -m pytest tests/ -q` | 通过，`60 passed` |
| 契约同步检查 | `powershell -ExecutionPolicy Bypass -File scripts/check-contract-sync.ps1` | 通过，`Contract sync check passed` |
| Java 测试 | `.\gradlew.bat test` | 通过，`BUILD SUCCESSFUL` |
| 旧问题残留扫描 | `rg 'reference_analysis.*waiting_asset_confirmation|repair.*next=repairing|从未|未调用|缺少触发' ...` | 未发现残留 |

说明：Python 测试时仍有 `.pytest_cache` 写入 warning，属于本地缓存目录权限问题，不影响测试结果。

---

## 6. 剩余风险

| 编号 | 风险 | 影响 | 后续阶段 |
|---|---|---|---|
| RISK-M4-001 | 真实 Temporal Server 集成测试尚未运行 | 当前验证覆盖了 Python Activity / Schema / Java 编译测试，但没有证明真实 Temporal Worker 在线时完整链路一定可跑通 | M11 |
| RISK-M4-002 | fake provider 返回的是占位资源 URL | 前端预览关键帧或视频片段时，可能看到占位或不可访问资源 | M5 / M6 |
| RISK-M4-003 | video clip workflow 目前传入的 `storyboard` 仍是空对象 | fake 模式不受影响；真实视频生成阶段需要补齐 storyboard / keyframe / clip prompt 的完整上下文 | M6 |

---

## 7. 给后续 AI 开发的注意事项

1. Java 是任务状态的唯一真实来源，Python callback 只能上报阶段结果，不能自行决定任意状态。
2. 新增或修改 callback `stage`、`nextTaskStatus`、payload 字段时，必须同步检查：
   - `docs/03-ai-output-json-schema.md`
   - `services/ai-orchestrator/src/schemas/ai_outputs.py`
   - `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java`
   - `scripts/check-contract-sync.ps1`
3. repair 不应该回到 `repairing` 自身，而应该进入“具体要重做的那一环”。
4. 如果真实视频生成要打开，必须先补齐 `VideoClipGenerationRequest.storyboard` 的真实内容，不能长期依赖空对象。
