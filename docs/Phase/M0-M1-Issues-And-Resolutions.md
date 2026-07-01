# M0 + M1 问题与解决方案统计

> 日期：2026-06-27
> 范围：Fashion Creative Loop V1 的 M0 当前项目审计 + M1 契约和数据库迁移
> 关联验收文档：`docs/Phase/M0-M1-Results-And-Acceptance.md`

## 1. 总体结论

本次复验发现，M0 审计部分基本可接受，但 M1 契约和数据库迁移存在多处跨文档不一致问题。

核心问题不是代码编译失败，而是：

```text
技术方案、数据库契约、OpenAPI、AI 输出契约、Flyway migration 没有完全对齐
```

如果不修复，后续 M2/M3/M4 开发会出现：

1. 前端传入的枚举被后端或数据库拒绝；
2. Python 回调 stage 无法被 Java 识别；
3. 素材角色无法表达服装业务需求；
4. 关键帧和视频片段状态语义混乱；
5. 后续 AI 编码会基于错误契约继续扩散问题。

本次已完成修复，并将 M0-M1 验收结论调整为：

```text
修复后有条件通过
```

剩余条件是：需要在真实 PostgreSQL 环境执行一次 `V3__fashion_creative_loop.sql`，确认 Flyway migration 可真实落库。

---

## 2. 问题统计

| 编号 | 问题 | 严重级别 | 状态 |
|---|---|---|---|
| M1-001 | TaskMode 与技术方案不一致 | 高 | 已修复 |
| M1-002 | `01-database-schema.sql` 主建表约束未真正加入 Fashion 状态 | 高 | 已修复 |
| M1-003 | `task_assets` 素材角色过于泛化，无法表达服装素材角色 | 高 | 已修复 |
| M1-004 | `creative_states` 未按服装垂类保存结构化状态 | 高 | 已修复 |
| M1-005 | keyframe/video_clip 状态设计与技术方案不一致 | 中 | 已修复 |
| M1-006 | AI callback stage 缺少关键阶段，且使用了不一致的 `fashion_qa` | 高 | 已修复 |
| M1-007 | OpenAPI 中 `RepairEvent`、`QaResult`、`VideoFingerprint` 仍是旧结构 | 中 | 已修复 |
| M1-008 | 验收文档结论过度乐观 | 中 | 已修复 |
| M1-009 | V3 migration 尚未在真实 PostgreSQL 执行 | 中 | 待复验 |

---

## 3. 详细问题与解决方案

### M1-001：TaskMode 与技术方案不一致

| 项目 | 内容 |
|---|---|
| 严重级别 | 高 |
| 问题位置 | `docs/02-openapi-spec.yaml`、`docs/01-database-schema.sql`、`V3__fashion_creative_loop.sql` |
| 原问题 | 技术方案定义 `PRODUCT_CREATIVE / REFERENCE_STORYBOARD / USER_SCRIPT / CUSTOM_STORYBOARD`，但 OpenAPI 和 DB 使用 `manual / ai_assisted / auto` |
| 直接后果 | 前端、Java、Python 无法围绕同一任务入口模式开发 |
| 修复方案 | 统一使用技术方案中的四种 TaskMode |
| 修复结果 | OpenAPI、DB Schema、Flyway migration 已统一 |

修复后的枚举：

```text
PRODUCT_CREATIVE
REFERENCE_STORYBOARD
USER_SCRIPT
CUSTOM_STORYBOARD
```

---

### M1-002：数据库主 Schema 没有真正加入新状态

| 项目 | 内容 |
|---|---|
| 严重级别 | 高 |
| 问题位置 | `docs/01-database-schema.sql` |
| 原问题 | Fashion 新状态只写在后面的注释 ALTER 中，主 `CREATE TABLE video_tasks` 约束仍是旧状态 |
| 直接后果 | 如果用 `01-database-schema.sql` 重建数据库，新状态不会生效 |
| 修复方案 | 把 Fashion 状态和扩展字段直接加入主 `video_tasks` 建表语句 |
| 修复结果 | 主 schema 与 V3 migration 已对齐 |

修复后新增状态包括：

```text
asset_uploading
asset_analyzing
waiting_asset_confirmation
reference_analyzing
plan_generating
storyboard_generating
waiting_storyboard_confirmation
keyframe_configuring
image_generating
waiting_image_confirmation
video_clip_generating
waiting_video_clip_confirmation
waiting_final_review
repairing
cancelled
```

---

### M1-003：task_assets 素材角色过于泛化

| 项目 | 内容 |
|---|---|
| 严重级别 | 高 |
| 问题位置 | `task_assets` schema、OpenAPI `TaskAsset`、V3 migration |
| 原问题 | 使用 `type=image/video/product_image` 和 `role=keyframe/video_clip/reference/product`，无法表达服装素材的业务角色 |
| 直接后果 | 后续素材绑定页、商品理解、参考视频分析无法准确区分正面图、背面图、模特图、场景图等 |
| 修复方案 | 改为 `asset_kind / asset_role / source` 三层结构 |
| 修复结果 | DB、OpenAPI、migration 已统一 |

修复后的核心字段：

```text
asset_kind
asset_role
source
```

修复后的素材角色包括：

```text
product_front
product_back
product_detail
model_reference
scene_reference
outfit_reference
reference_video
user_keyframe
generated_result
ai_keyframe
image_variant
video_clip
final_video
cover_image
```

---

### M1-004：creative_states 未保存服装垂类结构

| 项目 | 内容 |
|---|---|
| 严重级别 | 高 |
| 问题位置 | `creative_states` schema、OpenAPI `CreativeState`、V3 migration |
| 原问题 | 使用 `plan_context/storyboard_context/generation_params/reference_analysis`，偏泛化上下文 |
| 直接后果 | 无法稳定保存商品、模特、场景、搭配、约束和用户要求；后续局部修复容易丢失商品约束 |
| 修复方案 | 按服装垂类拆分状态字段 |
| 修复结果 | DB、OpenAPI、migration 已统一 |

修复后的字段：

```text
product_json
model_json
scene_json
outfit_json
reference_video_json
constraints_json
user_requirements_json
```

---

### M1-005：关键帧和视频片段状态语义不一致

| 项目 | 内容 |
|---|---|
| 严重级别 | 中 |
| 问题位置 | `keyframes`、`video_clips`、OpenAPI `Keyframe`、OpenAPI `VideoClip` |
| 原问题 | 状态使用 `pending/generating/completed/failed/rejected`，同时又用 `is_confirmed` 表示确认 |
| 直接后果 | 会出现 `completed=true` 但未确认、或确认状态和生成状态互相冲突的问题 |
| 修复方案 | 用单一状态表达完整生命周期 |
| 修复结果 | DB、OpenAPI、migration 已统一 |

修复后的状态：

```text
draft
generating
generated
uploaded
confirmed
rejected
failed
```

---

### M1-006：AI callback stage 不完整且命名不一致

| 项目 | 内容 |
|---|---|
| 严重级别 | 高 |
| 问题位置 | `docs/02-openapi-spec.yaml`、`docs/03-ai-output-json-schema.md` |
| 原问题 | 只新增 `keyframe/video_clip/repair/fashion_qa`，缺少素材分析、参考视频分析、创意方案等阶段 |
| 直接后果 | Python Fashion workflow 后续无法用标准 callback 回调 Java |
| 修复方案 | 补齐 Fashion Creative Loop callback stage，并将 `fashion_qa` 统一为 `qa` |
| 修复结果 | OpenAPI、AI output schema 已统一 |

修复后的新增阶段：

```text
asset_analysis
reference_analysis
creative_plan
keyframe
video_clip
qa
repair
```

---

### M1-007：部分 OpenAPI schema 仍是旧结构

| 项目 | 内容 |
|---|---|
| 严重级别 | 中 |
| 问题位置 | OpenAPI `RepairEvent`、`QaResult`、`VideoFingerprint` |
| 原问题 | OpenAPI 仍保留旧字段，例如 `roundNo`、`feedbackCategory`、`qualityScore`、`fingerprintHash` |
| 直接后果 | 前端生成类型会和数据库/技术方案不一致 |
| 修复方案 | 对齐数据库契约和技术方案 |
| 修复结果 | 已重新生成 `apps/web/src/types/api.generated.ts` |

---

### M1-008：验收文档结论过度乐观

| 项目 | 内容 |
|---|---|
| 严重级别 | 中 |
| 问题位置 | `docs/Phase/M0-M1-Results-And-Acceptance.md` |
| 原问题 | 文档写“已通过”“无契约差异”，但实际存在多处契约不一致 |
| 直接后果 | 后续 AI 编码会以错误结论继续开发 |
| 修复方案 | 将结论调整为“修复后有条件通过”，并新增复验修复记录 |
| 修复结果 | 验收文档已更新 |

---

### M1-009：V3 migration 尚未真实落库验证

| 项目 | 内容 |
|---|---|
| 严重级别 | 中 |
| 问题位置 | `apps/api-java/src/main/resources/db/migration/V3__fashion_creative_loop.sql` |
| 当前状态 | 待复验 |
| 原问题 | 当前只通过了静态检查、Java 测试和前端构建，尚未在真实 PostgreSQL 上执行 Flyway |
| 影响 | 不能 100% 证明 V3 migration 在真实数据库环境可执行 |
| 建议方案 | M2 前启动 Java 应用或连接测试 PostgreSQL，让 Flyway 执行 V3 |

---

## 4. 修改文件统计

| 文件 | 修改内容 |
|---|---|
| `docs/01-database-schema.sql` | 对齐主 schema、TaskMode、Fashion 状态、7 张表结构 |
| `docs/02-openapi-spec.yaml` | 对齐 TaskMode、VideoTaskStatus、TaskAsset、CreativeState、Keyframe、VideoClip、RepairEvent、VideoFingerprint、QaResult、AiCallbackRequest |
| `docs/03-ai-output-json-schema.md` | 补齐 callback stage、payload 字段和 nextTaskStatus |
| `apps/api-java/src/main/resources/db/migration/V3__fashion_creative_loop.sql` | 重写为与数据库契约一致的 Flyway migration |
| `apps/web/src/types/api.generated.ts` | 根据 OpenAPI 重新生成 |
| `docs/Phase/M0-M1-Results-And-Acceptance.md` | 调整验收结论，补充复验修复记录 |

---

## 5. 验证结果

| 验证项 | 结果 | 说明 |
|---|---|---|
| `npm run generate:api-types` | 通过 | OpenAPI 可正常生成前端类型 |
| `.\gradlew.bat test` | 通过 | Java 测试通过 |
| `npm run build` | 通过 | Next.js 生产构建通过 |
| `npx tsc --noEmit` | 通过 | TypeScript 类型检查通过 |
| 真实 PostgreSQL Flyway 执行 | 待复验 | 需要启动 Java 应用或测试数据库 |

---

## 6. 后续建议

### 6.1 进入 M2 前必须做

1. 使用真实 PostgreSQL 执行 `V3__fashion_creative_loop.sql`；
2. 确认 Flyway migration 无语法和约束冲突；
3. 确认数据库中 `video_tasks` 新字段和 7 张新表存在。

### 6.2 M2 开发重点

1. 新增 Java `TaskMode` 枚举；
2. 扩展 Java `VideoTaskStatus` 枚举；
3. 扩展 `VideoTaskStateMachine`；
4. 新增 `TaskAsset`、`CreativeState`、`Keyframe`、`VideoClip`、`RepairEvent` 等 Entity/Mapper/Service；
5. 扩展 AI callback DTO 和处理分支；
6. 保持 Java 是唯一业务状态源。

### 6.3 风险提醒

M1 是契约阶段。只要契约继续漂移，后续 M2/M3/M4 就会反复返工。

后续所有代码实现必须遵守：

```text
数据库 Schema、OpenAPI、AI Schema、Java DTO、前端类型必须同步更新
```

---

## 7. 当前结论

本次发现的问题已经完成契约层修复，当前状态为：

```text
M0 基本通过
M1 修复后有条件通过
```

条件是：

```text
真实 PostgreSQL 执行 V3 migration 通过后，M1 才可视为完全通过
```

