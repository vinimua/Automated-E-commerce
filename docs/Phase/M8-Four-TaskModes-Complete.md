# M8 验收文档：四种 taskMode 完整打通

**日期：** 2026-07-18
**状态：** ⚠️ 代码就绪，待端到端实测

---

## 验收标准

| # | 标准 | 状态 |
|---|---|---|
| 1 | 每种 taskMode 都能创建任务 | ✅ 代码审查通过 |
| 2 | 每种 taskMode 的初始输入都写入 `creative_state.userRequirements` | ✅ 代码审查通过 |
| 3 | 每种 taskMode 都走正确页面顺序 | ⚠️ 代码审查通过，待实测 |
| 4 | fake AI 输出能体现所选 taskMode，至少足够手工测试 | ⚠️ 仅 PRODUCT_CREATIVE 实测 |

> **实测覆盖：** `PRODUCT_CREATIVE`（task `770df3db-...`）完整跑通了资产→分析→方案→分镜→关键帧→片段→审核。其余三种模式仅完成代码审查，未创建任务实测。

---

## 一、四种 taskMode 流程

### 1. PRODUCT_CREATIVE（AI 根据商品生成创意）— 已实测

```
创建 → 上传素材 → AI 素材分析 → 确认分析结果 → AI 生成方案(3-5个)
→ 选择方案 → AI 生成分镜 → 确认分镜 → 关键帧配置 → 视频片段 → 渲染 → 审核
```

- `userRequirements.rawPrompt` → 用户创意方向（素材页 textarea）
- `userRequirements.taskMode` = `"PRODUCT_CREATIVE"`

---

### 2. REFERENCE_STORYBOARD（按参考视频分镜生成）— 待实测

```
创建（含参考视频URL） → 上传素材 → AI 素材分析 → 确认分析结果
→ AI 参考视频分析（拆解分镜） → AI 生成方案 → 选择方案
→ AI 生成分镜 → 确认分镜 → …（同 PRODUCT_CREATIVE 后续流程）
```

**M8 修复：** `AiCallbackServiceImpl.reference_analysis` case 原本只推进状态不触发方案生成，导致任务卡在 `plan_generating`。已加上 `startCreativePlanGeneration()` 调用。

数据流：
- 创建时：`referenceVideoUrl` → `creative_state.referenceVideoJson` + `TaskAsset(role=reference_video)`
- 确认素材时：`determineNextStatus()` 检测到 REFERENCE_STORYBOARD 模式 + 存在 reference_video 资产 → 返回 `reference_analyzing`
- 分析回调后：`referenceAnalysis` 写入 `creative_state` → 自动触发 `startCreativePlanGeneration()`
- 后续流程与 PRODUCT_CREATIVE 相同

---

### 3. USER_SCRIPT（用户提供脚本）— 待实测

```
创建（含脚本） → 上传素材 → AI 素材分析 → 确认分析结果
→ AI 生成方案（基于用户脚本） → 选择方案 → 分镜 → …（同 PRODUCT_CREATIVE）
```

数据流：
- 创建时：`scriptText` → `userRequirements.scriptText`
- Python 方案生成检测到 `taskMode=USER_SCRIPT`，将 `scriptText` 作为 `_primaryInput` 注入 prompt
- 方案以用户脚本为最高优先级创意来源

---

### 4. CUSTOM_STORYBOARD（用户提供分镜结构）— 待实测

```
创建（含分镜文本） → 上传素材 → AI 素材分析 → 确认分析结果
→ 直接进入 AI 生成分镜（跳过方案生成） → 确认分镜 → …（同 PRODUCT_CREATIVE）
```

**与另外三种模式的关键差异：** 确认素材后跳过「生成方案→选择方案」，直接进入分镜阶段。

**M8 新增：**
- `determineNextStatus()` 对 CUSTOM_STORYBOARD 返回 `storyboard_generating`（而非 `plan_generating`）
- `confirmAssets()` 新增 `storyboard_generating` 分支：自动创建合成方案 → 触发 `startStoryboardGeneration()`
- `VideoTaskStateMachine` 新增转换 `waiting_asset_confirmation → storyboard_generating`

数据流：
- 创建时：`storyboardText` → `userRequirements.storyboardText`
- 确认素材后：创建 `VideoPlanEntity(title="用户自定义分镜", hook=storyboardText前200字)` → 设置 `task.selectedPlanId` → 触发分镜生成
- Python 分镜生成从 `creative_context.userRequest.storyboardText` 获取用户原始结构

---

## 二、M8 代码改动

### Java

| 文件 | 改动 |
|---|---|
| `AiCallbackServiceImpl.java` | `reference_analysis` case 添加 `startCreativePlanGeneration()` 调用 + 注入 `CreativeContextAssembler`、`VideoMapper` |
| `TaskAssetServiceImpl.java` | `determineNextStatus()` 加 taskMode 判断：仅 REFERENCE_STORYBOARD 路由到 reference_analyzing；CUSTOM_STORYBOARD 路由到 storyboard_generating |
| `TaskAssetServiceImpl.java` | `confirmAssets()` 新增 `storyboard_generating` 分支：创建合成 VideoPlanEntity + 触发 startStoryboardGeneration |
| `TaskAssetServiceImpl.java` | 注入 `VideoPlanMapper`、`CreativeStateMapper` |
| `CreativeContextAssembler.java` | 上下文新增 `referenceAnalysis` 字段 |
| `VideoTaskStateMachine.java` | `waiting_asset_confirmation` 新增允许转换到 `storyboard_generating` |

### Python

| 文件 | 改动 |
|---|---|
| `generate_fashion_plans.py` | userPrompt 识别 `taskMode=USER_SCRIPT`/`CUSTOM_STORYBOARD`，提取 scriptText/storyboardText 作为 `_primaryInput` |

---

## 三、验证清单

| # | 项 | 状态 | 备注 |
|---|---|---|---|
| 1 | PRODUCT_CREATIVE 端到端 | ✅ 实测 | task `770df3db-...` 全流程跑通 |
| 2 | REFERENCE_STORYBOARD 创建+输入写入 | ✅ 代码审查 | referenceVideoUrl → creativeState + TaskAsset |
| 3 | REFERENCE_STORYBOARD 回调后触发方案生成 | ✅ 代码审查 | 修复了缺失的 `startCreativePlanGeneration()` |
| 4 | USER_SCRIPT 脚本写入 creative_state | ✅ 代码审查 | scriptText → userRequirements |
| 5 | USER_SCRIPT 脚本传给 AI prompt | ✅ 代码审查 | Python 检测 taskMode + _primaryInput |
| 6 | CUSTOM_STORYBOARD 跳过方案生成 | ✅ 代码审查 | determineNextStatus → storyboard_generating |
| 7 | CUSTOM_STORYBOARD 状态机转换 | ✅ 代码审查 | 新增 waiting_asset_confirmation → storyboard_generating |
| 8 | CUSTOM_STORYBOARD 自动创建合成方案 | ✅ 代码审查 | confirmAssets 创建 VideoPlanEntity + 触发分镜 |
| 9 | 四种模式在 assets 页显示对应 UI | ✅ 代码审查 | TASK_MODE_CONFIG 已定义四种文字 |

---

## 四、已知限制

| 限制 | 影响 | 建议 |
|---|---|---|
| 仅 PRODUCT_CREATIVE 实测 | 另三种模式可能存在未发现的运行时 bug | 创建 3 个测试任务，每种 mode 一个 |
| USER_SCRIPT 仍经过方案选择步骤 | 用户提供脚本后仍需从 3-5 个方案中选 | 可改为跳过选择，直接用脚本生成唯一方案 |
| CUSTOM_STORYBOARD 合成方案 title 固定 | 标题始终为"用户自定义分镜" | 可从 storyboardText 提取 |
| Reference Video 分析用 fixture | 非真实 AI 拆解参考视频 | M9 可接真实 LLM |
| 渲染需 RabbitMQ | 无 Docker 时只能用手动回调模拟 | `AiCallbackServiceImpl.simulateRenderComplete` 已有 fallback |

---

## 五、本 Session 配套修复（非 M8 范围）

以下 bug 在 M8 端到端测试中发现并修复，记录于此供追溯：

| 问题 | 修复 |
|---|---|
| keyframes/video_clips/videos/repair_events CHECK 约束值与 Java 代码不一致 | V16–V19 migration 对齐约束 |
| 配额重复消费导致 callback 500 | `QuotaServiceImpl` catch `DataIntegrityViolationException` + `REQUIRES_NEW` |
| `@Select` 绕过 MyBatis-Plus autoResultMap 导致 JSONB 列为空 | `StoryboardMapper`/`VideoPlanMapper` 改为 LambdaQueryWrapper |
| DeepSeek `ThinkingBlock` 无 `.text` 属性 | `_call_anthropic` 遍历 content blocks |
| `TaskProgress` 组件仅支持 legacy V1 阶段 | 改为双模式（legacy/fashion），根据 taskMode 切换 |
