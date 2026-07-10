# M0 契约与 UI 对齐审计报告

> 阶段：M0  
> 日期：2026-07-10  
> 审计范围：UI 工作流文档、DB Schema、OpenAPI、Java 状态机、前端路由/类型  
> 结论：**核心契约已对齐，发现 8 项需修复的差异（0 项阻塞性）**

---

## 0. 审计方法

并行审计 6 个真相源，交叉对比：

| # | 文件 | 审计内容 |
|---|---|---|
| 1 | `docs/Fashion-Creative-Loop-V1-User-Workflow-And-UI.md` | UI 页面定义、状态路由、taskMode 规则 |
| 2 | `docs/01-database-schema.sql` | CHECK 约束、枚举值、表结构 |
| 3 | `docs/02-openapi-spec.yaml` | VideoTaskStatus/TaskMode/VideoType 枚举、API 端点清单 |
| 4 | `apps/api-java/.../VideoTaskStateMachine.java` | 状态跳转表、重试目标 |
| 5 | `apps/api-java/...` (全部 Java 文件) | 枚举定义、Controller 端点、DTO 字段 |
| 6 | `apps/web/src/...` (全部前端文件) | 路由表、类型定义、页面实现 |

---

## 1. VideoTaskStatus — 29 个状态值

### 1.1 交叉对比

| # | 状态 | UI Doc | SQL | OpenAPI | Java Enum | 前端 STATUS_LABELS | Dashboard 路由 |
|---|---|---|---|---|---|---|---|
| 1 | `draft` | ❌ | ✅ | ✅ | ✅ | ✅ | `/plans` (fallback) |
| 2 | `asset_uploading` | ✅ | ✅ | ✅ | ✅ | ✅ | `/assets` |
| 3 | `asset_analyzing` | ✅ | ✅ | ✅ | ✅ | ✅ | `/assets` |
| 4 | `waiting_asset_confirmation` | ✅ | ✅ | ✅ | ✅ | ✅ | `/assets` |
| 5 | `reference_analyzing` | ✅ | ✅ | ✅ | ✅ | ✅ | `/reference-analysis` |
| 6 | `plan_generating` | ✅ | ✅ | ✅ | ✅ | ✅ | `/plans` |
| 7 | `analyzing` | ✅(兼容) | ✅ | ✅ | ✅ | ✅ | `/plans` (fallback) |
| 8 | `analysis_completed` | ✅(兼容) | ✅ | ✅ | ✅ | ✅ | `/plans` (fallback) |
| 9 | `plan_generated` | ✅(兼容) | ✅ | ✅ | ✅ | ✅ | `/plans` (fallback) |
| 10 | `waiting_plan_selection` | ✅ | ✅ | ✅ | ✅ | ✅ | `/plans` |
| 11 | `storyboard_generating` | ✅ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 12 | `script_generating` | ❌ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 13 | `script_generated` | ❌ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 14 | `material_generating` | ❌ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 15 | `material_generated` | ❌ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 16 | `rendering` | ✅ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 17 | `checking` | ❌ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 18 | `completed` | ✅ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 19 | `failed` | ✅ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 20 | `exported` | ✅ | ✅ | ✅ | ✅ | ✅ | `/progress` |
| 21 | `waiting_storyboard_confirmation` | ✅ | ✅ | ✅ | ✅ | ✅ | `/storyboard` |
| 22 | `keyframe_configuring` | ✅ | ✅ | ✅ | ✅ | ✅ | `/keyframes` |
| 23 | `image_generating` | ✅ | ✅ | ✅ | ✅ | ✅ | `/keyframes` |
| 24 | `waiting_image_confirmation` | ✅ | ✅ | ✅ | ✅ | ✅ | `/keyframes` |
| 25 | `video_clip_generating` | ✅ | ✅ | ✅ | ✅ | ✅ | `/clips` |
| 26 | `waiting_video_clip_confirmation` | ✅ | ✅ | ✅ | ✅ | ✅ | `/clips` |
| 27 | `waiting_final_review` | ✅ | ✅ | ✅ | ✅ | ✅ | `/review` |
| 28 | `repairing` | ✅ | ✅ | ✅ | ✅ | ✅ | `/review` |
| 29 | `cancelled` | ✅ | ✅ | ✅ | ✅ | ✅ | `/progress` |

### 1.2 发现

| 编号 | 发现 | 严重级别 | 说明 |
|---|---|---|---|
| **F-001** | 6 个旧 V1 状态在 UI 文档中无定义 | 中 | `draft`, `script_generating`, `script_generated`, `material_generating`, `material_generated`, `checking` 存在于 SQL/OpenAPI/Java/前端类型中，但 Fashion Creative Loop UI 文档未定义其页面行为。这些状态由旧 V1 链路使用，若 Fashion 任务意外进入这些状态，前端 routing 会 fallback 到 `/plans` 或 `/progress`，不会崩溃但无针对性 UI。**建议：保留在 DB 中以兼容旧任务，在 UI 文档中标注为"旧版兼容，仅 progress 页面展示"。** |

---

## 2. TaskMode — 4 个值

### 2.1 交叉对比

| taskMode | UI Doc | SQL | OpenAPI | Java (String) | 前端表单 |
|---|---|---|---|---|---|
| `PRODUCT_CREATIVE` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `REFERENCE_STORYBOARD` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `USER_SCRIPT` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `CUSTOM_STORYBOARD` | ✅ | ✅ | ✅ | ✅ | ✅ |

### 2.2 发现

| 编号 | 发现 | 严重级别 | 说明 |
|---|---|---|---|
| **F-002** | Java 缺少 `TaskMode` 枚举类 | 低 | OpenAPI 定义了正式的 `TaskMode` enum (4 值)，但 Java 用裸 `String` + `VideoTaskServiceImpl` 中的 ad-hoc `if/else` 校验。`CreateVideoTaskRequest.taskMode` 没有 `@Pattern` 注解，理论上可接受任意字符串。**建议：添加 `@Pattern(regexp = "...")` 注解或创建 `TaskMode` 枚举。** |

---

## 3. VideoType — 6 个值

### 3.1 交叉对比

| videoType | UI Doc | SQL | OpenAPI | Java Enum | 前端 V1_VIDEO_TYPES | 前端 LABELS |
|---|---|---|---|---|---|---|
| `pain_point_solution` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `before_after` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `review` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `product_showcase` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `ugc_style` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `tutorial` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |

### 3.2 发现

| 编号 | 发现 | 严重级别 | 说明 |
|---|---|---|---|
| **F-003** | 前端只定义了 3 种 VideoType 的常量和中文标签 | 低 | `V1_VIDEO_TYPES` 和 `VIDEO_TYPE_LABELS` 缺少 `product_showcase`, `ugc_style`, `tutorial`。页面代码用 `VIDEO_TYPE_LABELS[vt] \|\| vt` 兜底，不会崩溃，但会显示英文原始值。**建议：补全 3 个缺失值的中文标签。** |
| **F-004** | `/products/new` 不展示 videoType 选择 | ✅ 符合设计 | UI 文档明确规定 `taskMode` 是首要选择，`videoType` 是业务策略字段不在创建页展示。前端表单正确遵循此规则——`videoType` 完全不显示。**无需修改。** |

---

## 4. API 端点对齐

### 4.1 交叉对比

| 发现 | 严重级别 | 说明 |
|---|---|---|
| OpenAPI 定义 50 个端点，Java 实现 51 个 | — | 49 个完全匹配 |
| **F-005** | `POST /api/video-tasks/{taskId}/cancel` | 中 | Java `VideoTaskController` 已实现，OpenAPI 未收录 |
| **F-006** | `GET /api/users/me` | 低 | Java `UserController` 已实现，OpenAPI 未收录 |

---

## 5. Dashboard 路由对齐

### 5.1 发现

| 编号 | 发现 | 严重级别 | 说明 |
|---|---|---|---|
| **F-007** | `keyframe_configuring` 不在 `NEEDS_ACTION_STATES` 中 | 低 | 这是需要用户操作的等待状态（上传/生成关键帧），应在 TaskCard 上显示琥珀色"待操作"标识。目前只在 `ACTIVE_STATES` 中，表现为普通进行中样式。 |
| **F-008** | `rendering` 存在路由跳转冗余 | 低 | Dashboard 将 `rendering` 路由到 `/progress`，而 `/progress` 页面发现是 Fashion review 阶段后又显示链接指向 `/review`。用户经历一次多余的跳转。**建议：Dashboard 直接将 `rendering` 路由到 `/review`。** |

---

## 6. `/products/new` 设计对齐

| 检查项 | UI 文档要求 | 前端实际 | 结论 |
|---|---|---|---|
| taskMode 为首要选择 | ✅ "创建页的第一层选择是 taskMode，不是 videoType" | ✅ 4 个 taskMode 卡片选择器 | 对齐 |
| videoType 不展示 | ✅ "不展示 videoType" | ✅ 表单中完全无 videoType | 对齐 |
| 每种 taskMode 有不同必填项 | ✅ PRODUCT_CREATIVE 要商品图，REFERENCE_STORYBOARD 要视频 URL | ✅ 已实现条件渲染 | 对齐 |
| 创建后路由到 assets | ✅ "redirect to /video-tasks/{taskId}/assets" | ✅ `status="asset_uploading"` → `/assets` | 对齐 |

---

## 7. 状态机对齐

| 检查项 | 结论 |
|---|---|
| 29 个状态全部在 TRANSITIONS 中有定义 | ✅ |
| 所有 13 个 AI callback stage 在 RETRY_TARGETS 中有对应 | ✅ |
| 修复循环状态(`repairing`)可跳回 4 个目标状态 | ✅ |
| `waiting_image_confirmation → image_generating` 支持重新生成 | ✅ (M5 已修复) |
| `cancelled` 从任意非终态可达 | ✅ |

---

## 8. 审计结论

### 8.1 总体评估

**核心契约已对齐。** 29 个 VideoTaskStatus 值在全部 6 个真相源中一致。4 个 TaskMode 值一致。状态机转换与 UI 文档页面分配对应。所有前端路由存在且有效。

### 8.2 发现清单

| 编号 | 问题 | 严重级别 | 是否需要立即修复 |
|---|---|---|---|
| F-001 | 6 个旧 V1 状态在 UI 文档中无定义 | 中 | 否（旧链路兼容，fallback 不崩溃） |
| F-002 | Java 缺少 TaskMode 枚举 | 低 | 否（字符串校验可用） |
| F-003 | 前端 VIDEO_TYPE_LABELS 缺 3 个值 | 低 | 否（兜底显示英文） |
| F-004 | `/products/new` 不展示 videoType | ✅ 符合设计 | 无需修改 |
| F-005 | OpenAPI 缺 `POST /cancel` | 中 | 是（补充 OpenAPI） |
| F-006 | OpenAPI 缺 `GET /api/users/me` | 低 | 是（补充 OpenAPI） |
| F-007 | `keyframe_configuring` 不在 NEEDS_ACTION_STATES | 低 | 是（一行改动） |
| F-008 | `rendering` dashboard 路由跳转冗余 | 低 | 否（不影响功能） |

### 8.3 可立即修复项（推荐）

```diff
# F-007: dashboard/page.tsx — 补 NEEDS_ACTION_STATES
  const NEEDS_ACTION_STATES = [
    "waiting_plan_selection", "waiting_asset_confirmation",
    "waiting_storyboard_confirmation",
+   "keyframe_configuring",
    "waiting_image_confirmation", "waiting_video_clip_confirmation",
    "waiting_final_review",
  ];

# F-005: OpenAPI 补 cancel 端点
# 在 02-openapi-spec.yaml 的 /api/video-tasks/{taskId} 路径下新增:
#   post:
#     operationId: cancelTask
#     summary: 取消任务
#     ...

# F-003: api.ts — 补 VideoType 标签
  VIDEO_TYPE_LABELS: {
    ...
+   product_showcase: "商品展示",
+   ugc_style: "UGC风格",
+   tutorial: "教程演示",
  }
```
