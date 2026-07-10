# M5 问题与解决方案总结文档
> 阶段：M5 关键帧生图  
> 来源：对 `docs/Phase/M5-Results-And-Acceptance.md` 的复验，以及对 Java 后端、Python AI Orchestrator、OpenAPI、AI 输出 Schema、前端生成类型的交叉检查。  
> 结论：M5 原验收结论“已通过”过于乐观。复验发现关键帧生成/重生链路在状态流转、单帧重生、失败帧落库和契约同步上存在阻塞性问题。相关问题已完成修复，修复后构建、测试与契约同步检查通过。

---

## 1. 总体结论

M5 的目标是打通 Java 到 Python 的关键帧生成 workflow，并补齐批量生成、单帧重生、图像 Provider 抽象和相关契约。

复验后确认：

1. Python Provider 抽象、Fake/OpenAI Provider、基础 Schema 和测试框架已经具备可用基础。
2. Java 已新增关键帧批量生成和单帧重生接口，但原实现存在状态机、payload 范围和 callback 写库问题。
3. OpenAPI 和 AI JSON Schema 没有完全同步实际代码，导致“契约优先”链条断裂。
4. 前端已经调用 M5 新端点，但生成类型没有对应 path，说明前端类型与 OpenAPI 不一致。

修复后，M5 可调整为：

```text
修复后通过：关键帧批量生成、单帧重生、失败帧落库、Java/Python/OpenAPI/前端类型契约已对齐。
真实 Image API 端到端运行仍保留到 M11 验证。
```

---

## 2. 问题与解决方案

| 编号 | 问题 | 严重级别 | 问题原因 | 解决方案 |
|---|---|---|---|---|
| M5-001 | `waiting_image_confirmation` 状态下触发 generate/regenerate 不可靠 | 高 | `generateKeyframes()` 允许在 `waiting_image_confirmation` 触发，但没有切回 `image_generating`；`regenerateKeyframe()` 试图切回 `image_generating`，但状态机不允许 | 状态机新增 `waiting_image_confirmation -> image_generating`；generate/regenerate 在该状态下统一先切到 `image_generating` |
| M5-002 | 单帧 regenerate 实际会触发全量 storyboard 生成 | 高 | Java 只重置目标 keyframe，但仍把完整 storyboard 发给 Python；Python prompt activity 对所有 shots 生成 prompt | Java 构造 storyboard payload 时按目标 `shotNo` 过滤；payload 增加 `targetShotNos`；Python prompt activity 尊重 `targetShotNos` |
| M5-003 | callback 会按 `shotNo` 找第一条 keyframe 并错误更新 | 高 | Java callback 使用 `findByTaskId()` 后按 `shotNo` 过滤，未限定当前版本，也未限定目标 keyframe | 新增 `findByTaskIdAndShotNoAndVersion()`；callback 按 `taskId + shotNo + currentVersion` 更新当前版本 keyframe |
| M5-004 | callback 会无意义自增 keyframe version | 高 | 原 callback 找到已有 keyframe 后执行 `kf.setVersion(kf.getVersion() + 1)`，可能破坏当前版本确认链路和唯一约束语义 | 移除 callback 中的 version 自增；生成结果只更新当前版本对应 keyframe |
| M5-005 | 单帧失败没有写入 `failed/errorMessage` | 高 | Python 支持返回 `status=failed`，但 Java callback 只处理 `completed`，忽略 failed item | Java callback 增加 failed 分支，保存 `status=failed`、`errorMessage`、`provider`、`modelName`、`imagePurpose` |
| M5-006 | 失败帧缺少 provider/model 信息 | 中 | Python `fake_generate_keyframes` 在异常分支只返回错误信息，没有 provider/modelName | Python failed item 增加 `provider` 和 `modelName`，便于 Java 落库与排障 |
| M5-007 | OpenAPI 缺少 M5 新端点 | 高 | Java Controller 已新增 `/keyframes/generate` 和 `/keyframes/{keyframeId}/regenerate`，但 `docs/02-openapi-spec.yaml` 未同步 | OpenAPI 增加两个 POST path，并重新生成前端 `api.generated.ts` |
| M5-008 | AI JSON Schema 与 Python 实际输出不一致 | 高 | Python keyframe item 输出 `source`、`imagePurpose`，但 `docs/03-ai-output-json-schema.md` 对 keyframe item 使用 `additionalProperties: false` 且未声明这两个字段 | AI JSON Schema 增加 `source` 和 `imagePurpose` 字段 |
| M5-009 | 前端生成类型缺少新端点 | 中 | `api.generated.ts` 由旧 OpenAPI 生成，不包含 M5 新 path | 执行 `npm run generate:api-types`，同步前端类型 |
| M5-010 | M5 验收文档中的测试证据过期 | 中 | 文档记录 Python `68/68 passed`，当前实际测试为 `93 passed, 12 skipped` | 在本问题总结中记录最新验证结果；建议后续同步更新 M5 验收文档 |

---

## 3. 修复后的关键链路

### 3.1 批量生成关键帧

修复前：

```text
POST /api/video-tasks/{taskId}/keyframes/generate
  -> Java 创建缺失 draft keyframe
  -> 如果任务已在 waiting_image_confirmation，则不切换状态
  -> Python callback 回来时 Java 幂等守卫可能认为 keyframe 阶段已处理
  -> 新生成结果可能被忽略
```

修复后：

```text
POST /api/video-tasks/{taskId}/keyframes/generate
  -> Java 找出当前版本未配置的 storyboard shots
  -> Java 为目标 shots 创建 draft keyframe
  -> Java 将任务切到 image_generating
  -> Java 只发送目标 shots 给 Python
  -> Python 只生成目标 shots
  -> Java callback 按当前版本 shotNo 更新 keyframe
  -> Java 推进到 waiting_image_confirmation
```

### 3.2 单帧重生

修复前：

```text
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate
  -> Java 重置目标 keyframe 为 generating
  -> Java 发送完整 storyboard
  -> Python 为所有 shots 生成图片
  -> Java callback 可能覆盖非目标 keyframe，并重复消耗额度
```

修复后：

```text
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate
  -> Java 校验 keyframe 必须是 rejected 或 failed
  -> Java 重置该 keyframe 为 generating
  -> Java 将任务切到 image_generating
  -> Java 只发送该 keyframe 对应 shotNo
  -> Python 只生成该 shotNo
  -> Java callback 只更新当前版本该 shotNo 的 keyframe
```

### 3.3 失败帧处理

修复前：

```text
Python 返回 keyframe status=failed
  -> Java callback 忽略该 item
  -> 任务仍进入 waiting_image_confirmation
  -> 用户看不到具体失败帧和 errorMessage
```

修复后：

```text
Python 返回 keyframe status=failed
  -> Java callback 更新对应 keyframe 为 failed
  -> 保存 errorMessage/provider/modelName/imagePurpose
  -> 用户可对 failed keyframe 发起 regenerate
```

---

## 4. 修改文件清单

### 4.1 Java 后端

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/state/VideoTaskStateMachine.java` | 增加 `waiting_image_confirmation -> image_generating` 状态流转 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/mapper/KeyframeMapper.java` | 新增 `findByTaskIdAndShotNoAndVersion()` |
| `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/service/impl/KeyframeServiceImpl.java` | 批量生成和单帧重生改为目标 shot payload；补充 `targetShotNos`；等待确认状态下允许重新进入生图 |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | keyframe callback 按当前版本更新；移除 version 自增；补充 failed item 落库 |
| `apps/api-java/src/test/java/com/tk/ai/video/module/videotask/state/VideoTaskStateMachineTest.java` | 增加 `waiting_image_confirmation -> image_generating` 测试用例 |

### 4.2 Python AI Orchestrator

| 文件 | 修改内容 |
|---|---|
| `services/ai-orchestrator/src/activities/generate_keyframe_prompts.py` | 支持 `targetShotNos`，只为目标 shots 生成 prompt |
| `services/ai-orchestrator/src/activities/fake_generate_keyframes.py` | failed item 增加 `provider` 和 `modelName` |

### 4.3 契约与前端类型

| 文件 | 修改内容 |
|---|---|
| `docs/02-openapi-spec.yaml` | 增加 `POST /api/video-tasks/{taskId}/keyframes/generate` 和 `POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/regenerate` |
| `docs/03-ai-output-json-schema.md` | keyframe generation result item 增加 `source` 和 `imagePurpose` |
| `apps/web/src/types/api.generated.ts` | 由 OpenAPI 重新生成，包含 M5 新端点 |

---

## 5. 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| Java 测试 | `.\gradlew.bat test` | 通过，`BUILD SUCCESSFUL` |
| Python 测试 | `python -m pytest tests/ -q` | 通过，`93 passed, 12 skipped` |
| 前端构建 | `npm run build` | 通过，Next.js build 成功 |
| OpenAPI 类型生成 | `npm run generate:api-types` | 通过，`api.generated.ts` 已更新 |
| 契约同步检查 | `.\scripts\check-contract-sync.ps1` | 通过，仍有既有 AssetKind/AssetSource drift 警告 |

说明：Python 测试仍出现 `.pytest_cache` 写入 warning，这是本地缓存目录权限问题，不影响测试结果。

---

## 6. 剩余风险

| 编号 | 风险 | 影响 | 后续阶段 |
|---|---|---|---|
| RISK-M5-001 | 真实 Image API 端到端尚未运行 | Fake provider 已验证，但真实 OpenAI/兼容图像模型、API key、返回 URL 可访问性仍未验证 | M11 |
| RISK-M5-002 | keyframe callback 仍以 `shotNo + currentVersion` 匹配，不包含 keyframeId | 当前单版本单 shot 一帧模型下可用；如果未来同一 shot 支持 first/last/reference 多帧并行，需要扩展 callback 标识 | 后续 keyframe 多用途增强 |
| RISK-M5-003 | M5 验收文档本身尚未同步最新结果 | `M5-Results-And-Acceptance.md` 仍可能包含过期测试数量和“已通过”自评 | 文档收尾 |
| RISK-M5-004 | 契约同步脚本未检查 path 级 API 缺失 | 本次 OpenAPI 缺 path 未被 `check-contract-sync.ps1` 捕捉，说明脚本目前偏 enum 检查 | 后续质量增强 |
| RISK-M5-005 | 工作区存在大量未提交改动 | 本次修复与其他未提交改动混在同一工作区，提交时需注意分组 | 提交整理 |

---

## 7. 给后续 AI 开发的注意事项

1. keyframe regenerate 必须是目标帧操作，不应再次发送完整 storyboard。
2. Java callback 写 keyframe 时必须限定当前版本，禁止在 callback 中随意自增 `version`。
3. Python 输出新增字段时必须同步 `docs/03-ai-output-json-schema.md`，尤其是在 `additionalProperties: false` 的对象中。
4. Java Controller 新增端点时必须同步 `docs/02-openapi-spec.yaml`，并重新生成 `apps/web/src/types/api.generated.ts`。
5. failed item 不应被忽略。只要 Python 返回了结构化失败结果，Java 就必须保存可见状态和错误信息。
6. `waiting_image_confirmation` 不只是终点，也可能是局部重生的起点；状态机必须显式允许回到对应生成状态。

