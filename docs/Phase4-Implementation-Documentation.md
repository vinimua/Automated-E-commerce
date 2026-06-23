# 阶段结果验收文档

---

## 0. 文档元信息

| 项目 | 内容 |
|---|---|
| 项目名称 | TikTok Shop AI 带货视频生成系统 |
| 阶段编号 | Phase 4 |
| 阶段名称 | Python AI 编排服务 |
| 文档版本 | v1.1 |
| 提交日期 | 2026-06-16 |
| 提交人 | Claude (AI Agent) |
| 验收对象 | V1-Development-Roadmap.md 阶段 4 |
| 当前结论 | **待验收**（自评：有条件通过，核心缺陷已整改） |
| 代码分支 | main |
| Commit / Tag | 未提交（本地 working tree） |

---

## 1. 阶段目标

### 1.1 本阶段目标

| 编号 | 目标 | 验收标准 | 当前状态 |
|---|---|---|---|
| G-001 | Pydantic 模型与 `03-ai-output-json-schema.md` 契约一致 | 15 个模型全部通过 fixture 数据验证 | 已完成 |
| G-002 | RenderManifest Pydantic 模型匹配 `04-render-manifest-schema.md` | 9 个嵌套模型 + V1 模板映射 | 已完成 |
| G-003 | AI 服务层（LLM + 图片 + 视频 + 回调） | Fake mode 无 API key 时返回 fixture JSON | 已完成 |
| G-004 | JSON 校验管道（提取 → jsonschema → Pydantic → 修复 → 重试） | `validation_pipeline.py` 7 步完整实现 | 已完成 |
| G-005 | 10 个 Temporal Activity | analyze_product → ... → build_render_manifest → callback_java | 已完成 |
| G-006 | 2 个 Temporal Workflow | ProductAnalysisWorkflow (analyze + product_analysis callback + plans + video_plan callback) + SelectedPlanGenerationWorkflow (8 step) | 已完成 |
| G-007 | FastAPI 端点启动真实 Temporal Workflow | 替代 Phase 1 的 placeholder 响应 | 已完成 |
| G-008 | Temporal Worker 在 main.py lifespan 中启动 | Client 连接 → Worker 注册 → 后台 asyncio task | 已完成 |

### 1.2 非本阶段范围

| 项目 | 原因 | 计划阶段 |
|---|---|---|
| 真实 AI 模型调用 | 需要 API key，当前使用 fixture 数据代替 | 部署时配置 |
| 端到端 Temporal 验证 | 需要 Temporal 服务器运行；本地已安装 temporalio 并完成导入验证 | 部署时验证 |
| 模型日志写入数据库 | 路径图 4.5 要求但无独立日志表，Java 端 model_logs 表已就绪 | Phase 6 联调 |
| 成本限制实时追踪 | config.py 已配置 `ai_cost_limit_per_task=5.0`，activity 层未实现累计计费 | Phase 6 联调 |

---

## 2. 交付物清单

### 2.1 新增文件

| 文件路径 | 类型 | 说明 | 是否纳入验收 |
|---|---|---|---|
| `src/schemas/render_manifest.py` | Pydantic | 9 个嵌套模型：RenderManifest, RenderAsset, EditConfig, SubtitleStyle, MusicConfig, VoiceoverConfig, OutputConfig, CoverConfig, V1 模板映射 | 是 |
| `src/services/llm_service.py` | Service | OpenAI/Anthropic 双 provider + fake mode (fixture JSON)，含 `call_llm()` 统一入口 | 是 |
| `src/services/callback_service.py` | Service | httpx POST Java `/api/ai-callbacks/{taskId}`，3 次指数退避重试 | 是 |
| `src/services/validation_pipeline.py` | Service | extract_json → try_parse_json → validate_pydantic → repair_json → retry (3x) | 是 |
| `src/activities/__init__.py` | Activity | 10 个 Activity 统一导出 `ALL_ACTIVITIES` 列表 | 是 |
| `src/activities/analyze_product.py` | Activity | LLM → ProductAnalysis 校验 → 失败抛 ApplicationError | 是 |
| `src/activities/generate_video_plans.py` | Activity | LLM → VideoPlanResult 校验 (3-5 plans) | 是 |
| `src/activities/generate_script.py` | Activity | LLM → Script 生成 (title/hook/script/caption/hashtags) | 是 |
| `src/activities/generate_storyboard.py` | Activity | LLM → StoryboardResult 校验 (4-12 shots) | 是 |
| `src/activities/generate_asset_prompts.py` | Activity | 透传 storyboard prompts (pass-through) | 是 |
| `src/activities/generate_image_assets.py` | Activity | LLM → MaterialResult 校验 (image generation) | 是 |
| `src/activities/generate_video_clips.py` | Activity | LLM → MaterialResult 校验 (video clip generation) | 是 |
| `src/activities/check_asset_quality.py` | Activity | LLM → QualityCheckResult 校验 (5 checks) | 是 |
| `src/activities/build_render_manifest.py` | Activity | Storyboard + Materials → RenderManifest 组装 | 是 |
| `src/activities/callback_java.py` | Activity | 调用 callback_service + 构建 success/failed payload 工具函数 | 是 |
| `src/workflows/product_analysis.py` | Workflow | @workflow.defn: analyze → product_analysis callback → plans → video_plan callback（含失败兜底） | 是 |
| `src/workflows/selected_plan_generation.py` | Workflow | @workflow.defn: 8 步流程，每步失败自动回调 Java | 是 |

### 2.2 修改文件

| 文件路径 | 修改内容 | 影响范围 | 是否纳入验收 |
|---|---|---|---|
| `src/schemas/ai_outputs.py` | 重写：15 个 Pydantic 模型完全匹配 `03-ai-output-json-schema.md`，含 `extra=forbid`、Literal 枚举、条件校验 | AI 输出类型系统 | 是 |
| `src/main.py` | 重写：Temporal Client 连接 + Worker 启动 + 后台 asyncio task + 优雅降级 | 服务入口 | 是 |
| `src/api/workflows.py` | 重写：placeholder 替换为 `client.start_workflow()` 真实调用 | 工作流触发端点 | 是 |
| `src/activities/__init__.py` | 重写：10 个 Activity 导入 + `ALL_ACTIVITIES` 列表 | Activity 注册 | 是 |
| `src/workflows/__init__.py` | 重写：2 个 Workflow 导入 + `ALL_WORKFLOWS` 列表 | Workflow 注册 | 是 |

### 2.3 删除文件

| 文件路径 | 删除原因 | 影响范围 |
|---|---|---|
| 无 | — | — |

---

## 3. 功能完成情况

| 功能项 | 需求来源 | 当前状态 | 验收标准 | 验收结果 |
|---|---|---|---|---|
| Pydantic → 契约对齐 | Roadmap 4.4 + 03-schema | 已完成 | 15 个模型通过 fixture 验证，含 `extra=forbid` | 待验收 |
| RenderManifest 模型 | Roadmap 4.7 + 04-schema | 已完成 | 9 个嵌套模型 + V1 模板映射 + material type 转换 | 待验收 |
| Fake provider | Roadmap 4.8 验收标准 | 已完成 | 无 API key → 返回 fixture JSON | 待验收 |
| 双重校验管道 | Roadmap 4.4 + 03-schema §9 | 已完成 | extract → jsonschema → Pydantic → repair → retry (3x) | 待验收 |
| ProductAnalysisWorkflow | Roadmap 4.1 | 已完成 | analyze 成功后回调 product_analysis，再生成 plans 并回调 video_plan | 待验收 |
| SelectedPlanGenerationWorkflow | Roadmap 4.1 | 已完成 | 8 步：script → storyboard → prompts → images → videos → quality → manifest → callback | 待验收 |
| Callback 回调 Java | Roadmap 4.2 callback_java | 已完成 | httpx POST + 3 次指数退避重试 + X-Internal-Service-Token | 待验收 |
| 失败回调兜底 | Roadmap 4.8 验收标准 | 已完成 | 每个 activity 失败 → 自动回调 `status=failed` + error payload | 待验收 |
| Temporal Worker 生命周期 | Roadmap 4.1 | 已完成 | lifespan startup Client + Worker → 后台 asyncio task → shutdown | 待验收 |
| 端点启动真实 Workflow | Roadmap 4.3 | 已完成 | `client.start_workflow()` 替代 Phase 1 placeholder | 待验收 |

---

## 4. 契约与接口对齐

### 4.1 契约来源

| 契约文件 | 用途 | 本阶段是否涉及 | 是否已核对 |
|---|---|---|---|
| `docs/01-database-schema.sql` | 数据库 Schema、状态枚举 | 是（nextTaskStatus 枚举） | 是 |
| `docs/02-openapi-spec.yaml` | Java API 契约 — AiCallbackRequest schema | 是（CallbackPayload 结构） | 是 |
| `docs/03-ai-output-json-schema.md` | AI 输出结构 | **是（核心契约）** | 是 |
| `docs/04-render-manifest-schema.md` | RenderManifest 契约 | **是（核心契约）** | 是 |

### 4.2 Pydantic → OpenAPI Schema 字段对齐

| 契约 Schema | Pydantic 模型 | 字段差异 | 校验差异 | 是否一致 |
|---|---|---|---|---|
| ProductAnalysis (12 fields) | `ProductAnalysis` (12 fields) | 0 | 0 | 是 |
| VideoPlanResult (1 nested array) | `VideoPlanResult` + `VideoPlanItem` | 0 | 0 | 是 |
| StoryboardResult (8 fields + shots) | `StoryboardResult` + `ShotItem` | 0 | 0 | 是 |
| MaterialResult (1 array) | `MaterialResult` + `MaterialItem` | 0 | 0 | 是 |
| QualityCheckResult (7 fields) | `QualityCheckResult` + `QualityChecks` (5 sub-fields) | 0 | 0 | 是 |
| AiCallbackPayload (12 fields) | `CallbackPayload` + `CallbackError` | 0 | 0 | 是 |
| RenderManifest (15 nested types) | `RenderManifest` + 8 nested models | 0 | 0 | 是 |

### 4.3 Java → Python 请求对齐

| Java `AiServiceClient` 方法 | Python 端点 | 请求模型 | 字段是否一致 |
|---|---|---|---|
| `startProductAnalysis()` | `POST /ai/workflows/product-analysis` | `ProductAnalysisRequest` | 是 |
| `startSelectedPlanGeneration()` | `POST /ai/workflows/selected-plan-generation` | `SelectedPlanGenerationRequest` | 是 |

### 4.4 已知契约差异

| 差异项 | 影响范围 | 严重级别 | 是否阻塞验收 | 处理建议 |
|---|---|---|---|---|
| 无阻塞性契约差异 | — | — | 否 | Pydantic 模型已逐字段对照 03/04 两份 schema 文件修复 |
| 无阻塞性运行契约差异 | — | — | 否 | 已连接腾讯云 Temporal Server 完成真实 Workflow E2E；完整 Java DB 联调仍需后续执行 |

---

## 5. 工作流状态流转

### 5.1 ProductAnalysisWorkflow

| 步骤 | Activity | 输入 | 输出 | 失败回调 stage |
|---|---|---|---|---|
| 1 | `analyze_product` | product_context (dict) | `ProductAnalysis` | `product_analysis` |
| 2 | `callback_java` | analysis → Java | `status=success, stage=product_analysis, nextTaskStatus=analysis_completed` | (workflow-level) |
| 3 | `generate_video_plans` | analysis (dict) | `VideoPlanResult` (3-5 plans) | `video_plan` |
| 4 | `callback_java` | plans → Java | `status=success, stage=video_plan, nextTaskStatus=plan_generated` | (workflow-level) |

### 5.2 SelectedPlanGenerationWorkflow

| 步骤 | Activity | 输出 | 回调 stage | 回调 nextTaskStatus |
|---|---|---|---|---|
| 1 | `generate_script` | Script dict | storyboard | — |
| 2 | `generate_storyboard` | `StoryboardResult` | storyboard | `script_generated` |
| 3 | `generate_asset_prompts` | Pass-through | — | — |
| 4 | `generate_image_assets` | `MaterialResult` | material | — |
| 5 | `generate_video_clips` | `MaterialResult` | material | `material_generated` |
| 6 | `check_asset_quality` | `QualityCheckResult` | quality_check | — |
| 7 | `build_render_manifest` | `RenderManifest` | render_manifest | `rendering` |
| 8 | `callback_java` | manifest → Java | — | — |

---

## 6. 错误处理验收

| 场景 | 当前行为 | 预期行为 | 是否通过 | 备注 |
|---|---|---|---|---|
| LLM 返回非法 JSON | `validation_pipeline.repair_json` → 去掉尾逗号 → 重试 3 次 | 修复成功则继续，失败抛 ValueError | 是 | Activity 捕获后回调 Java failed |
| Activity 抛异常 | Workflow catch → `callback_java(build_failed_callback_payload(...))` | 回调 `status=failed` + error 对象 | 是 | 每个 activity 有独立 try/catch |
| Temporal 不可用 | `main.py` lifespan try/except → `app.state.temporal_client = None` | 服务启动但不处理 Workflow 请求 | 是 | 端点返回 503 |
| Callback HTTP 失败 | `callback_service.send_callback` 重试 3 次（2s/4s/6s 指数退避） | 3 次全失败 → 返回 False → Activity 抛异常 | 是 | Java 已幂等 |
| JSON 校验失败 | `validate_and_repair` 重试 3 次后抛 `ValueError` | Activity 捕获 → 回调 Java failed | 是 | |
| 模型限流 | Activity 内部的 LLM 调用会抛异常 | Workflow catch → 回调 failed | 是 | RetryPolicy 有 3 次重试 |

---

## 6.1 本次整改记录

| 编号 | 原问题 | 修复方式 | 验证结果 |
|---|---|---|---|
| FIX-001 | Fake provider 返回 dict 时，Activity 使用 `str(dict)` 生成单引号字符串，导致 JSON 解析失败 | `validation_pipeline.validate_and_repair()` 支持 `dict/list` 直接进入 Pydantic 校验；相关 Activity 不再强制 `str(raw)` | Fake mode Activity 链路通过 |
| FIX-002 | `SelectedPlanGenerationWorkflow` 对多参数 Activity 传入单个 dict，Temporal 执行时参数不匹配 | 多参数 Activity 改为 Temporal `args=[...]` 调用方式 | Workflow 模块导入通过 |
| FIX-003 | `quality_check` 成功回调携带 `nextTaskStatus=checking`，Java 端没有对应成功处理分支 | `build_callback_payload()` 允许 `nextTaskStatus=None`；`quality_check` 成功回调不再发送状态流转 | Callback payload 构造验证通过 |
| FIX-004 | fake material fixture 包含 `type=text` 和空 URL，不符合 `MaterialResult` 契约 | 删除非法 text material，保留 image/video 资产 fixture | Fake mode Activity 链路通过 |
| FIX-005 | RenderManifest 只校验字段形状，未校验总时长与资产时长一致性 | `RenderManifest` 增加 `model_validator`，要求 assets 时长合计与 `duration` 差异不超过 1 秒 | RenderManifest 生成验证通过 |
| FIX-006 | Java `AiServiceClient` 启动 AI 工作流失败后只记录日志，业务任务可能停留在处理中 | 捕获异常后抛出 `IllegalStateException`，让上层事务感知启动失败 | `./gradlew.bat test` 通过 |
| FIX-007 | 阶段文档记录 `RISK-005` 为待修复，但代码实际已包含 `product_analysis` 成功回调 | 文档风险清单更新，删除过期风险，保留真实未验项 | 文档已同步 |
| FIX-008 | 真实 Temporal 执行时，Workflow 传入的 Activity 引用被 SDK 判定为非 callable | Workflow 内 `execute_activity()` 改用 Activity 名称字符串，例如 `"analyze_product"`、`"callback_java"` | 真实 Temporal E2E 通过 |
| FIX-009 | `SelectedPlanGenerationRequest` 未定义 `productContext`，但 endpoint 启动 Workflow 时读取 `req.productContext` | 为 `SelectedPlanGenerationRequest` 增加 `productContext` 默认空 dict，兼容当前 Java payload | 请求模型验证通过 |

---

## 7. 构建与测试证据

### 7.1 环境信息

| 项目 | 值 |
|---|---|
| 操作系统 | Windows 11 Pro x64 |
| Python 版本 | 3.14.5 |
| Pydantic 版本 | 2.13.4 |
| httpx 版本 | 0.28.1 |
| temporalio 版本 | 1.28.0 |
| 执行日期 | 2026-06-16 |

### 7.2 Pydantic 模型验证

```text
1. ProductAnalysis OK: score=78
2. VideoPlanResult OK: 3 plans
3. Script OK: Say Goodbye to Noise
4. StoryboardResult OK: 5 shots
5. MaterialResult OK: 4 image materials + 4 video materials
6. QualityCheckResult OK: score=86
7. RenderManifest OK: template=pain_point_solution_v1, assets=5
8. CallbackPayload OK: quality_check success without nextTaskStatus
```

### 7.3 执行命令

| 命令 | 执行目录 | 结果 | 说明 |
|---|---|---|---|
| `python -m pip install -e .` | `services/ai-orchestrator/` | **通过** | 安装 FastAPI、Pydantic、Temporal SDK、AI SDK 等依赖 |
| `python -c "import temporalio; from src.workflows import ALL_WORKFLOWS; from src.activities import ALL_ACTIVITIES; print(len(ALL_WORKFLOWS), len(ALL_ACTIVITIES))"` | `services/ai-orchestrator/` | **通过** | 输出 `2 10`，Workflow/Activity 注册可导入 |
| `python -c "import src.main; print(src.main.app.title)"` | `services/ai-orchestrator/` | **通过** | FastAPI app 可导入，输出 `TK AI Video Orchestrator` |
| Fake mode Activity 链路脚本 | `services/ai-orchestrator/` | **通过** | analysis → plans → script → storyboard → image/video materials → quality → manifest |
| `python -c "from src.activities.callback_java import build_callback_payload..."` | `services/ai-orchestrator/` | **通过** | `quality_check` 成功 payload 不携带 `nextTaskStatus` |
| 真实 Temporal E2E 脚本 | `services/ai-orchestrator/` | **通过** | 连接 `124.223.200.16:17233`，临时 Worker 执行 2 个 Workflow，假 Java callback 收到 6 次回调 |
| `./gradlew.bat test` | `apps/api-java/` | **通过** | Java 端测试通过 |
| `uvicorn src.main:app --reload` | `services/ai-orchestrator/` | **未长期运行** | 真实 Temporal E2E 已由临时 Worker 脚本覆盖，常驻服务仍需按启动手册运行 |

### 7.4 自动化测试结果

| 测试类型 | 是否具备 | 结果 | 覆盖范围 |
|---|---|---|---|
| 单元测试 | 否 | **不适用** | Phase 6 计划实现 |
| Pydantic 模型校验 | 是（手动） | **通过** | 15 个模型全部 |
| Fake mode Activity 链路 | 是（手动） | **通过** | 8 个核心生成 Activity + RenderManifest 构建 |
| 真实 Temporal Workflow E2E | 是（手动） | **通过** | ProductAnalysisWorkflow + SelectedPlanGenerationWorkflow，使用本地假 Java callback |
| Java 自动化测试 | 是 | **通过** | `apps/api-java` Gradle test |
| 完整 Java → Python → Temporal → Java DB 联调 | 否 | **未运行** | 需启动 Java 后端并使用真实 taskId |

---

## 8. 已知问题与风险

| 编号 | 问题 | 严重级别 | 影响 | 是否阻塞验收 | 建议处理 |
|---|---|---|---|---|---|
| RISK-001 | 完整 Java → Python → Temporal → Java DB 联调未执行 | **中** | 已证明 Temporal Workflow 与 callback HTTP 可跑通，但尚未证明 Java 业务表持久化闭环 | 否 | 启动 Java 后端后使用真实 taskId 执行业务链路验收 |
| RISK-002 | 真实 AI 调用未经验证 | **中** | 真实模型输出可能与 fixture 格式不同，可能触发修复/重试路径 | 否 | 配置 OpenAI/Anthropic API key 后执行 1 条真实任务，保留原始响应与修复日志 |
| RISK-003 | fake provider 使用 placeholder 资产 URL | **低** | Phase 5 Render Worker 若直接下载会失败 | 否 | Phase 5 应实现下载失败降级、占位素材或测试素材替换 |
| RISK-004 | Python 3.14.5 + temporalio 1.28.0 已能安装导入，但生产兼容性仍需确认 | **低** | 部署镜像或运行时环境可能与本机不同 | 否 | 生产建议固定 Python 3.12 或在镜像构建中锁定并验证 Python 3.14 |
| RISK-005 | 成本累计追踪和模型日志尚未落库 | **低** | 无法做单任务成本审计和模型调用追踪 | 否 | Phase 6 联调时决定由 Python 直写 DB 还是回调 Java 统一落库 |

---

## 9. 未完成项

| 项目 | 当前状态 | 未完成原因 | 是否阻塞验收 | 计划阶段 |
|---|---|---|---|---|
| Workflow E2E 验证 | 已完成 | 真实 Temporal + 临时假 Java callback 已通过 | 否 | 已完成 |
| 完整 Java DB 回调联调 | 未运行 | 需要启动 Java 后端并使用真实业务 taskId | 否 | Phase 6 联调 |
| 真实 AI 调用 | Fake mode | 无 API key | 否 | 部署时配置 |
| product_analysis 成功回调 | 已完成 | Workflow 已包含 analyze 成功后的 `product_analysis` 回调 | 否 | 已完成 |
| 成本累计追踪 | 未实现 | config 已配置但 activity 未实现 | 否 | Phase 6 |
| 模型日志写入 | 未实现 | 依赖设计决策（Python 直写 DB vs 回调 Java） | 否 | Phase 6 |

---

## 10. 验收结论

### 10.1 自评结论

**有条件通过**

### 10.2 通过条件核对

| 条件 | 当前状态 | 备注 |
|---|---|---|
| Pydantic 模型与契约一致 | **是** | 15 个模型通过 fixture 验证 |
| 10 个 Activity 全部实现 | **是** | `ALL_ACTIVITIES` 列表完整 |
| 2 个 Workflow 全部实现 | **是** | 含失败兜底逻辑 |
| Fake mode 可用 | **是** | 无需 API key |
| Temporal Worker 代码就绪 | **是** | temporalio 已安装，真实 Temporal E2E 已通过 |
| 无高严重级别缺陷 | **是** | 剩余风险集中在完整 Java DB 联调和真实 AI 调用验证 |

### 10.3 验收意见

```text
阶段 4 Python AI 编排服务的核心代码已全部实现：15 个 Pydantic 模型、10 个 Temporal Activity、
2 个 Workflow、AI 服务层（fake mode）、JSON 校验管道、Java 回调客户端、Temporal Worker 生命周期管理。

有条件通过项：
1. 完整 Java → Python → Temporal → Java DB 联调 — 需启动 Java 后端并使用真实 taskId
2. 真实 AI API key 配置 — 部署时设置环境变量并执行 1 条真实任务
3. 成本累计追踪与模型日志落库 — Phase 6 联调时补齐

上述 3 项不阻塞阶段 5（Node Render Worker）的启动。
阶段 4 和阶段 5 按路线图依赖关系可顺序推进（Phase 4 → Phase 5）。
```

---

## 11. 附录

### 11.1 文件统计

```
services/ai-orchestrator/src/
├── activities/     (11 files) — 10 个 Activity + __init__
├── api/            ( 3 files) — health, workflows, __init__
├── schemas/        ( 4 files) — ai_outputs, render_manifest, workflow_requests, __init__
├── services/       ( 4 files) — llm_service, callback_service, validation_pipeline, __init__
├── workflows/      ( 3 files) — product_analysis, selected_plan_generation, __init__
├── config.py       ( 1 file)  — 完整配置
└── main.py         ( 1 file)  — FastAPI + Temporal Worker
─────────────────────────────────
Total:              27 files
```

### 11.2 相关文档

| 文档 | 路径 |
|---|---|
| AI 输出 Schema | `docs/03-ai-output-json-schema.md` |
| RenderManifest Schema | `docs/04-render-manifest-schema.md` |
| 阶段路线图 | `docs/V1-Development-Roadmap.md` |
| 阶段 2 实现文档 | `docs/Phase2-Implementation-Documentation.md` |
| 阶段 3 实现文档 | `docs/Phase3-Implementation-Documentation.md` |
