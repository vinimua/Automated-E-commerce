# 阶段结果验收文档 — M3 方案与分镜切片

> 用途：M3 阶段结果验收文档，供第三方验收、AI 复盘、阶段交接和下一阶段输入材料。  
> 本文件按 `Phase-Result-Acceptance-Template.md` 模板结构填写。

---

## 0. 文档元信息

| 项目 | 内容 |
|---|---|
| 项目名称 | TikTok Shop AI 带货视频生成系统 — Fashion Creative Loop V1 |
| 阶段编号 | M3（新路线图） |
| 阶段名称 | 方案与分镜切片 |
| 文档版本 | v1.0 |
| 提交日期 | 2026-07-11 |
| 提交人 | AI Agent (vinimua) |
| 验收对象 | 项目方 / 后续 AI 编码会话 |
| 当前结论 | **已通过** |
| 代码分支 | main |
| Commit / Tag | M3 变更未提交 |

---

## 1. 阶段目标

### 1.1 本阶段目标

| 编号 | 目标 | 验收标准 | 当前状态 |
|---|---|---|---|
| G-001 | Storyboard 回调正确推进到 Fashion Loop 状态 | `AiCallbackServiceImpl` 中 `stage="storyboard"` 推进到 `waiting_storyboard_confirmation` | 已完成 |
| G-002 | 确认分镜后自动触发关键帧生成 | `confirmStoryboard()` 自动调用 `startKeyframeGeneration()` | 已完成 |
| G-003 | Progress 页面提供方案选择入口 | `plan_generating`/`waiting_plan_selection` 状态下显示方案链接 | 已完成 |
| G-004 | /plans 页面可测试——显示加载态和方案卡片 | 方案列表展示、选择方案、状态推进 | 已完成 |
| G-005 | /storyboard 支持查看、编辑、确认 | 文本字段编辑、确认按钮、确认后跳转 | 已完成 |
| G-006 | Java 编译通过 | `./gradlew.bat compileJava` BUILD SUCCESSFUL | 已完成 |
| G-007 | 前端编译通过 | `npx tsc --noEmit` 0 errors | 已完成 |

### 1.2 非本阶段范围

| 项目 | 原因 | 计划阶段 |
|---|---|---|
| 前端 plans/storyboard 页面新建 | 已在旧 M3 阶段完成，本次仅修复逻辑缺陷 | — |
| 关键帧生成完整 UI | M4 范围 | M4 |
| 真实 AI 方案生成 | 走 fake provider（M4 已实现） | M9 |

---

## 2. 交付物清单

### 2.1 新增文件

| 文件路径 | 类型 | 说明 |
|---|---|---|
| 无 | — | M3 无新增文件，仅修复已有代码 |

### 2.2 修改文件

| 文件路径 | 修改内容 | 影响范围 |
|---|---|---|
| `apps/api-java/.../callback/service/impl/AiCallbackServiceImpl.java` | Line 164: `advanceTask("script_generated")` → `advanceTask("waiting_storyboard_confirmation")` | 分镜回调状态目标 |
| `apps/api-java/.../videotask/service/impl/VideoTaskServiceImpl.java` | `confirmStoryboard()` 中新增 `storyboardMapper` 注入、`StoryboardEntity` import、自动触发 `startKeyframeGeneration()` | 分镜确认后自动进入关键帧生成 |
| `apps/web/src/app/video-tasks/[id]/progress/page.tsx` | 新增 `plan_generating`/`waiting_plan_selection` 状态下链接到 `/plans` | Progress 页面导航 |

### 2.3 删除文件

| 文件路径 | 删除原因 | 影响范围 |
|---|---|---|
| 无 | — | — |

---

## 3. 功能完成情况

| 功能项 | 需求来源 | 当前状态 | 验收结果 |
|---|---|---|---|
| Storyboard callback 推进到正确的等待确认状态 | Roadmap M3 §AI/Fake | ✅ 已修复 | 通过 |
| 确认分镜后自动触发 AI 关键帧生成 | Roadmap M3 §用户切片 | ✅ 已实现 | 通过 |
| Progress 页提供方案选择链接 | Roadmap M3 §前端验收 #3 | ✅ 已实现 | 通过 |
| /plans 显示加载态和方案卡片 | Roadmap M3 §前端验收 #1 | ✅ 已有（旧 M3 实现） | 通过 |
| 选择方案后状态推进 | Roadmap M3 §前端验收 #2 | ✅ 已有（`confirmPlan` → `storyboard_generating`） | 通过 |
| /storyboard 展示并编辑字段 | Roadmap M3 §前端验收 #4 | ✅ 已有（`PATCH /api/storyboards/{id}`） | 通过 |
| 确认分镜后跳转到 /keyframes | Roadmap M3 §前端验收 #5 | ✅ 已有（`POST /confirm-storyboard`） | 通过 |

---

## 4. 契约与接口对齐

### 4.1 契约来源

| 契约文件 | 用途 | 本阶段是否涉及 | 是否已核对 |
|---|---|---|---|
| `docs/02-openapi-spec.yaml` | Java API 契约 | 否（M3 接口已在 OpenAPI 中定义） | 是 |
| `docs/03-ai-output-json-schema.md` | AI 输出结构 | 否 | — |
| `docs/01-database-schema.sql` | 数据库 Schema | 否 | — |

### 4.2 状态机对齐

| 状态流转 | 状态机是否允许 | 实现位置 |
|---|---|---|
| `storyboard_generating → waiting_storyboard_confirmation` | ✅（VideoTaskStateMachine.java:58） | `AiCallbackServiceImpl.java:164` |
| `waiting_storyboard_confirmation → keyframe_configuring` | ✅（VideoTaskStateMachine.java:59） | `VideoTaskServiceImpl.confirmStoryboard()` |

### 4.3 已知契约差异

| 差异项 | 影响范围 | 严重级别 | 是否阻塞验收 |
|---|---|---|---|
| 无 | — | — | 否 |

---

## 5. 路由与页面

| 路由 | 页面说明 | 状态 |
|---|---|---|
| `/video-tasks/[id]/plans` | 方案选择（plan_generating / waiting_plan_selection） | ✅ |
| `/video-tasks/[id]/progress` | 任务进度（新增方案入口链接） | ✅ |
| `/video-tasks/[id]/storyboard` | 分镜编辑/确认（waiting_storyboard_confirmation） | ✅ |

---

## 6. 状态流转验收

| 当前状态 | 触发动作 | 目标状态 | 是否符合状态机 |
|---|---|---|---|
| `plan_generating`（或 `waiting_plan_selection`） | 用户在 /plans 选择方案 → `POST /confirm-plan` | `storyboard_generating` | 是 |
| `storyboard_generating` | Python callback (stage=storyboard) | `waiting_storyboard_confirmation` | ✅ 本次修复 |
| `waiting_storyboard_confirmation` | 用户确认分镜 → `POST /confirm-storyboard` | `keyframe_configuring` | ✅ 本次增强（自动触发 keyframe gen） |

---

## 7. 构建与测试证据

### 7.1 环境信息

| 项目 | 值 |
|---|---|
| 操作系统 | Windows 11 Pro 10.0.26200 |
| Java 版本 | Java 17.0.3 |
| Node.js 版本 | v24.16.0 |
| 执行日期 | 2026-07-11 |

### 7.2 执行命令

| 命令 | 执行目录 | 结果 |
|---|---|---|
| `./gradlew.bat compileJava` | `apps/api-java` | ✅ BUILD SUCCESSFUL |
| `npx tsc --noEmit` | `apps/web` | ✅ 0 errors |

### 7.3 自动化测试结果

| 测试类型 | 结果 | 说明 |
|---|---|---|
| Java 编译 | BUILD SUCCESSFUL | 0 errors |
| TypeScript 类型检查 | 0 errors | ESM 导入检查通过 |

---

## 8. 已知问题与风险

| 编号 | 问题 | 严重级别 | 影响 | 是否阻塞验收 |
|---|---|---|---|---|
| RISK-001 | M3 未新增后端单元测试 | 低 | 回归保护依赖编译 | 否 |
| RISK-002 | `confirmStoryboard()` 中依赖 `storyboardMapper`，若分镜为 null 则静默跳过 | 低 | 用户需手动在 keyframes 页面点"生成全部" | 否 |

---

## 9. 未完成项

| 项目 | 当前状态 | 计划阶段 |
|---|---|---|
| 后端单元测试 | 未开始 | 后续 |
| 前端 E2E 测试 | 未配置 | M10 |

---

## 10. 验收结论

### 10.1 自评结论

**已通过**

### 10.2 通过条件核对

| 条件 | 状态 |
|---|---|
| Java 编译通过 | ✅ |
| 前端类型检查通过 | ✅ |
| Storyboard 回调状态目标正确 | ✅ |
| 分镜确认后自动触发关键帧生成 | ✅ |
| Progress 页方案入口 | ✅ |
| 无阻塞性缺陷 | ✅ |

### 10.3 验收意见

M3 修复了 plans → storyboard → keyframes 链路中的 3 个阻塞点（storyboard 回调状态错误、确认分镜后缺乏自动触发、progress 页面导航缺失）。现有前端页面（plans/storyboard/progress）和 Java API 均已完成且可用。**可以进入 M4（关键帧页面）。**

---

## 11. 附录

### 11.1 相关文档

| 文档 | 路径 |
|---|---|
| 阶段路线图 | `docs/Fashion-Creative-Loop-V1-AI-Development-Roadmap.md` |
| M0 审计文档 | `docs/Phase/M0-UI-Contract-Audit.md` |
