# Phase 4 问题与解决方案汇总

---

## 0. 文档元信息

| 项目 | 内容 |
|---|---|
| 项目名称 | TikTok Shop AI 带货视频生成系统 |
| 阶段编号 | Phase 4 |
| 阶段名称 | Python AI 编排服务 |
| 文档用途 | 汇总阶段 4 验收发现的问题、修复方案、验证结果和剩余风险 |
| 生成日期 | 2026-06-16 |
| 关联阶段文档 | `docs/Phase4-Implementation-Documentation.md` |
| 当前结论 | 核心缺陷已整改，阶段 4 可有条件通过；真实 Temporal E2E 已通过，完整 Java DB 联调和真实 AI 调用仍需补验 |

---

## 1. 总体结论

阶段 4 的主要目标是实现 Python FastAPI + Temporal AI 编排服务，包括 Pydantic 契约模型、AI fake provider、JSON 校验管道、10 个 Activity、2 个 Workflow、Java 回调和 Temporal Worker 生命周期。

验收时发现的问题集中在 4 类：

| 类型 | 说明 | 当前状态 |
|---|---|---|
| 数据校验链路问题 | fake provider 返回 dict，但旧逻辑按字符串 JSON 处理，导致校验失败 | 已修复 |
| Temporal 调用问题 | Workflow 对多参数 Activity 的调用方式不符合 Temporal Python SDK 要求 | 已修复 |
| 回调契约问题 | `quality_check` 成功回调携带了 Java 不处理的 `nextTaskStatus=checking` | 已修复 |
| 验收文档不准确 | 文档记录了已经不存在的 RISK-005，且测试证据偏乐观 | 已修复 |

整改后已完成导入验证、fake mode Activity 链路验证、callback payload 验证、真实 Temporal Workflow E2E 和 Java Gradle 测试。仍未完成完整 Java DB 回调联调和真实 AI API 调用验证。

---

## 2. 已修复问题清单

### 2.1 Fake provider 返回 dict 导致 JSON 解析失败

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-001 |
| 严重级别 | 高 |
| 问题位置 | `services/ai-orchestrator/src/services/validation_pipeline.py`、多个 Activity |
| 原问题 | fake provider 返回的是 Python `dict`，但 Activity 使用 `str(raw)` 转成字符串后再走 JSON 解析 |
| 直接后果 | `str(dict)` 生成的是单引号字符串，不是合法 JSON，导致 fake mode 本地链路无法跑通 |
| 修复方案 | `validate_and_repair()` 支持 `dict/list` 直接进入 Pydantic 校验；Activity 不再强制 `str(raw)` |
| 验证结果 | fake mode Activity 链路通过 |

涉及文件：

| 文件 | 修复点 |
|---|---|
| `src/services/validation_pipeline.py` | `validate_and_repair()` 接收 `str | dict | list` |
| `src/activities/analyze_product.py` | 移除 `str(raw)` |
| `src/activities/generate_video_plans.py` | 移除 `str(raw)` |
| `src/activities/generate_storyboard.py` | 移除 `str(raw)` |
| `src/activities/generate_image_assets.py` | 移除 `str(raw)` |
| `src/activities/generate_video_clips.py` | 移除 `str(raw)` |
| `src/activities/check_asset_quality.py` | 移除 `str(raw)` |

---

### 2.2 SelectedPlanGenerationWorkflow 多参数 Activity 调用错误

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-002 |
| 严重级别 | 高 |
| 问题位置 | `services/ai-orchestrator/src/workflows/selected_plan_generation.py` |
| 原问题 | Workflow 调用多参数 Activity 时，把多个业务参数包成一个 dict 传入 |
| 直接后果 | Temporal 真正执行时会出现 Activity 参数不匹配，Workflow 无法完成 |
| 修复方案 | 使用 Temporal Python SDK 的 `args=[...]` 方式传递多参数 |
| 验证结果 | Workflow 模块导入通过；Activity 注册验证通过 |

已修复的 Activity 调用包括：

| Activity | 修复后传参方式 |
|---|---|
| `generate_script` | `args=[plan, product_context]` |
| `generate_storyboard` | `args=[script, plan, product_context, duration, video_type]` |
| `generate_video_clips` | `args=[prompts, image_assets]` |
| `check_asset_quality` | `args=[prompts, combined_materials]` |
| `build_render_manifest` | `args=[task_id, video_type, storyboard, combined_materials]` |

---

### 2.3 quality_check 回调状态不符合 Java 契约

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-003 |
| 严重级别 | 中 |
| 问题位置 | `services/ai-orchestrator/src/workflows/selected_plan_generation.py`、`src/activities/callback_java.py` |
| 原问题 | `quality_check` 成功回调携带 `nextTaskStatus=checking` |
| 直接后果 | Java 端没有 `quality_check` 推进 `checking` 的成功处理分支，状态语义不清晰 |
| 修复方案 | `build_callback_payload()` 允许 `nextTaskStatus=None`；`quality_check` 成功回调只传质量结果，不推进任务状态 |
| 验证结果 | callback payload 构造验证通过，输出中不再包含 `nextTaskStatus` |

修复后的语义：

| stage | 是否推进任务状态 | 说明 |
|---|---|---|
| `quality_check` | 否 | 只回传质量检查结果 |
| `render_manifest` | 是 | 回传 manifest 后推进到 `rendering` |

---

### 2.4 fake material fixture 不符合 MaterialResult 契约

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-004 |
| 严重级别 | 中 |
| 问题位置 | `services/ai-orchestrator/src/services/llm_service.py` |
| 原问题 | fake fixture 中包含 `type=text` 和空 URL |
| 直接后果 | `MaterialResult` 契约只接受合法素材类型和可用 URL，fixture 会导致校验失败或后续渲染失败 |
| 修复方案 | 删除非法 text material，保留 image/video 素材 fixture |
| 验证结果 | fake mode Activity 链路通过，生成 4 个 image materials + 4 个 video materials |

---

### 2.5 RenderManifest 缺少总时长一致性校验

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-005 |
| 严重级别 | 中 |
| 问题位置 | `services/ai-orchestrator/src/schemas/render_manifest.py` |
| 原问题 | RenderManifest 只校验字段结构，没有校验 `duration` 与 assets 时长总和是否一致 |
| 直接后果 | 可能生成结构合法但时间轴不可信的 manifest，导致 Phase 5 Render Worker 渲染异常 |
| 修复方案 | 增加 `model_validator`，要求 assets 时长合计与 manifest `duration` 差异不超过 1 秒 |
| 验证结果 | RenderManifest fake 链路生成通过 |

---

### 2.6 Java AiServiceClient 启动 AI 工作流失败后吞异常

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-006 |
| 严重级别 | 高 |
| 问题位置 | `apps/api-java/src/main/java/com/tk/ai/video/common/AiServiceClient.java` |
| 原问题 | Java 调用 Python AI Orchestrator 失败后只记录 warn，不向上抛异常 |
| 直接后果 | 业务层可能认为任务启动成功，但 AI Workflow 实际未启动，任务会卡在处理中 |
| 修复方案 | catch 后抛出 `IllegalStateException`，让上层事务感知启动失败 |
| 验证结果 | `./gradlew.bat test` 通过 |

---

### 2.7 阶段文档记录了过期风险

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-007 |
| 严重级别 | 中 |
| 问题位置 | `docs/Phase4-Implementation-Documentation.md` |
| 原问题 | 文档记录 `ProductAnalysisWorkflow` 缺少 `product_analysis` 成功回调，但代码实际已经包含 |
| 直接后果 | 第三方验收会被误导，无法准确判断真实剩余风险 |
| 修复方案 | 更新阶段文档为 v1.1，新增整改记录，删除过期风险，重写测试证据和剩余风险 |
| 验证结果 | 文档已同步 |

实际代码行为：

| ProductAnalysisWorkflow 步骤 | 回调 |
|---|---|
| `analyze_product` 成功后 | 回调 `stage=product_analysis`，`nextTaskStatus=analysis_completed` |
| `generate_video_plans` 成功后 | 回调 `stage=video_plan`，`nextTaskStatus=plan_generated` |

---

### 2.8 Workflow 真实执行时 Activity 引用不可调用

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-008 |
| 严重级别 | 高 |
| 问题位置 | `services/ai-orchestrator/src/workflows/product_analysis.py`、`services/ai-orchestrator/src/workflows/selected_plan_generation.py` |
| 原问题 | Workflow 内使用从 `src.activities` 导入的函数引用调用 `workflow.execute_activity()` |
| 直接后果 | 真实 Temporal 执行时报错 `Activity must be a string or callable`，Workflow 反复失败直到超时 |
| 修复方案 | Workflow 内所有 `execute_activity()` 改为使用 Activity 名称字符串，例如 `"analyze_product"`、`"callback_java"` |
| 验证结果 | 真实 Temporal E2E 通过 |

说明：

```text
这个问题不会在普通 Python import 检查中暴露，必须真实连接 Temporal Server 并执行 Workflow 才能发现。
```

---

### 2.9 selected-plan-generation 请求模型缺少 productContext

| 项目 | 内容 |
|---|---|
| 问题编号 | P4-009 |
| 严重级别 | 中 |
| 问题位置 | `services/ai-orchestrator/src/schemas/workflow_requests.py`、`services/ai-orchestrator/src/api/workflows.py` |
| 原问题 | endpoint 启动 `SelectedPlanGenerationWorkflow` 时读取 `req.productContext`，但 `SelectedPlanGenerationRequest` 没有定义该字段 |
| 直接后果 | Java 触发选中方案生成时，Python endpoint 会出现属性错误 |
| 修复方案 | `SelectedPlanGenerationRequest` 增加 `productContext: dict = Field(default_factory=dict)`，兼容当前 Java payload |
| 验证结果 | 请求模型构造验证通过；真实 Temporal E2E 通过 |

后续建议：

```text
当前修复保证 Phase 4 可运行。真实 AI 调用时建议 Java 侧补充完整 productContext，
否则脚本和分镜生成只能依赖 selectedPlan 中的信息，商品上下文不够完整。
```

---

## 3. 验证证据

### 3.1 Python 依赖与导入验证

| 验证项 | 结果 |
|---|---|
| `temporalio` 安装 | 通过 |
| `ALL_WORKFLOWS` 导入 | 通过，数量为 2 |
| `ALL_ACTIVITIES` 导入 | 通过，数量为 10 |
| FastAPI app 导入 | 通过，标题为 `TK AI Video Orchestrator` |

### 3.2 Fake mode Activity 链路验证

已验证链路：

```text
analyze_product
→ generate_video_plans
→ generate_script
→ generate_storyboard
→ generate_image_assets
→ generate_video_clips
→ check_asset_quality
→ build_render_manifest
```

验证输出摘要：

```text
analysis 78
plans 3
script Say Goodbye to Noise
shots 5
images 4
videos 4
quality 86
manifest pain_point_solution_v1 5
```

### 3.3 Callback payload 验证

验证目标：

```text
quality_check 成功回调不携带 nextTaskStatus
```

验证结果：

```text
{
  "taskId": "00000000-0000-0000-0000-000000000001",
  "schemaVersion": "1.0.0",
  "stage": "quality_check",
  "status": "success",
  "qualityCheck": {
    "qualityScore": 86
  }
}
```

### 3.4 Java 测试

| 命令 | 结果 |
|---|---|
| `./gradlew.bat test` | 通过 |

### 3.5 真实 Temporal Workflow E2E 验证

验证方式：

```text
连接腾讯云 Temporal Server: 124.223.200.16:17233
启动临时 Worker，注册当前 2 个 Workflow 和 10 个 Activity
启动本地临时 HTTP callback server，模拟 Java callback endpoint
执行 ProductAnalysisWorkflow 和 SelectedPlanGenerationWorkflow
```

验证结果：

```text
PRODUCT_RESULT {"stage": "video_plan", "status": "completed"}
SELECTED_RESULT {"stage": "render_manifest", "status": "completed"}
CALLBACK_COUNT 6
CALLBACK /api/ai-callbacks/{taskId} product_analysis success analysis_completed
CALLBACK /api/ai-callbacks/{taskId} video_plan success plan_generated
CALLBACK /api/ai-callbacks/{taskId} storyboard success script_generated
CALLBACK /api/ai-callbacks/{taskId} material success material_generated
CALLBACK /api/ai-callbacks/{taskId} quality_check success None
CALLBACK /api/ai-callbacks/{taskId} render_manifest success rendering
```

---

## 4. 当前仍需补充验证的问题

### 4.1 完整 Java DB 回调联调未完成

| 项目 | 内容 |
|---|---|
| 当前状态 | 未完成 |
| 原因 | 当前已用临时 HTTP callback server 验证 callback HTTP，但未启动 Java 后端使用真实 taskId 写入业务表 |
| 影响 | 尚未证明 Java `AiCallbackServiceImpl` 对所有 callback payload 的持久化闭环 |
| 建议验收方式 | 启动 Java 后端，通过真实业务任务执行 Java → Python → Temporal → Java callback → DB 状态更新 |

当前腾讯云服务器 Temporal 状态已确认：

| 服务 | 地址 |
|---|---|
| Temporal gRPC | `124.223.200.16:17233` |
| Temporal UI | `http://124.223.200.16:18088` |

本机启动 Python Worker 时应设置：

```powershell
$env:TEMPORAL_HOST="124.223.200.16:17233"
$env:TEMPORAL_NAMESPACE="default"
$env:TEMPORAL_TASK_QUEUE="ai-video-task-queue"
$env:JAVA_API_BASE_URL="http://localhost:8080"
python -m uvicorn src.main:app --reload --port 8000
```

### 4.2 真实 AI 调用未完成

| 项目 | 内容 |
|---|---|
| 当前状态 | 未完成 |
| 原因 | 当前使用 fake provider，无真实 OpenAI/Anthropic API key |
| 影响 | 真实模型输出可能有格式偏差，可能触发 JSON 修复和重试逻辑 |
| 建议验收方式 | 配置 API key 后执行 1 条真实任务，保存原始 AI 输出、修复日志、最终 Pydantic 校验结果 |

### 4.3 成本累计和模型日志未落库

| 项目 | 内容 |
|---|---|
| 当前状态 | 未完成 |
| 原因 | config 已有成本限制，但 Activity 层未实现累计计费；模型日志落库策略未定 |
| 影响 | 暂时无法进行单任务成本审计和模型调用追踪 |
| 建议处理 | Phase 6 联调时决定由 Python 直写 DB，还是由 Python 回调 Java 后统一落库 |

---

## 5. 阶段 4 当前验收判断

| 验收项 | 当前判断 | 说明 |
|---|---|---|
| Pydantic 契约模型 | 通过 | fixture 校验通过 |
| RenderManifest 模型 | 通过 | 已增加时长一致性校验 |
| 10 个 Activity | 通过 | fake mode 链路通过，真实 Temporal E2E 通过 |
| 2 个 Workflow | 通过 | 导入和注册通过，真实 Temporal E2E 通过 |
| Java 回调 payload | 通过 | `quality_check` 状态语义已修复 |
| Java 启动 AI 工作流失败处理 | 通过 | 已改为抛异常 |
| Temporal Worker | 通过 | 已连接真实 Temporal Server 并完成 2 个 Workflow E2E |
| 真实 AI 调用 | 未验收 | 需配置 API key 后补验 |

最终结论：

```text
阶段 4 核心代码缺陷已完成整改，可进入有条件通过状态。
正式通过仍需要补充完整 Java DB 回调联调和真实 AI 调用验证。
这些剩余项不阻塞 Phase 5 Render Worker 的开发，但会影响 Phase 6 联调验收。
```

---

## 6. 后续建议

| 优先级 | 建议 | 原因 |
|---|---|---|
| P0 | 执行完整 Java DB 回调联调 | 证明 Java 业务状态和持久化闭环完整 |
| P1 | 配置真实 AI API key 做 1 条真实任务 | 验证模型输出是否稳定匹配契约 |
| P1 | 为 validation pipeline 增加单元测试 | 防止后续 Prompt 或 schema 修改打破 JSON 修复链路 |
| P1 | 为 Workflow 参数传递增加最小测试 | 防止再次出现多参数 Activity 传参错误 |
| P2 | 明确模型日志和成本追踪归属 | Phase 6 联调需要可追溯性 |
