# Fashion Creative Loop V1 用户工作流与界面约定

> 版本：1.2.0
> 日期：2026-07-08
> 目的：固定 V1 的用户入口、页面职责、4 种 taskMode 的完整用户链路、前端展示逻辑，避免后续开发反复把"工作流模式""视频类型""素材上传"混在同一个表单里。
> 关联文档：
> - `docs/Fashion-Creative-Loop-V1-AI-Development-Roadmap.md`
> - `docs/01-database-schema.sql`
> - `docs/02-openapi-spec.yaml`

---

## 1. 核心原则

本项目要解决的是同质化模板视频问题，所以用户创建任务时不应该先选择模板或视频类型，而应该先表达"我要怎么创作"。

创建页的第一层选择是 `taskMode`，不是 `videoType`。

| 字段 | 面向对象 | 含义 | 是否在创建页直接让用户选择 |
|---|---|---|---|
| `taskMode` | 用户工作流 | 用户想用哪种创作路径完成视频 | 是 |
| `videoType` | 业务策略 / 旧链路兼容 | 痛点型、卖点型等视频表达类型，可由 AI 或后续方案决定 | 否 |
| `template` | 渲染 Worker | Remotion 渲染模板 | 否 |

不可再把 `taskMode`、`videoType`、`template` 放在同一层让用户选择。

### 1.1 防模板化职责分层

V1 的反模板化不是靠无限增加 `videoType`，而是把职责拆开：

| 层级 | 负责什么 | 不负责什么 |
|---|---|---|
| `taskMode` | 用户入口：用户想用哪种创作路径开始 | 不决定成片模板 |
| `creative plan` | 差异化：角度、卖点、钩子、节奏、证据方式 | 不直接绑定渲染模板 |
| `storyboard` | 具体结构：每个镜头做什么、展示什么素材、讲什么文案 | 不作为固定模板复用 |
| `template` | 最终渲染样式：字幕、转场、版式、输出规格 | 不决定创意方向 |

因此，前端创建页只能把 `taskMode` 作为第一层用户选择；`videoType` 可以存在于后端、AI 输出、统计和渲染兼容链路里，但不应该作为 V1 用户创建任务时的主选择器。

---

## 2. 用户旅程总览

整个 V1 流程分为两个阶段：

```
┌─ 创建向导（/products/new）────────────────────┐
│ Step 1: 选择创作方式 taskMode                   │
│ Step 2: 填写最小创建信息                         │
│ Submit: 创建任务                                 │
└────────────────────────────────────────────────┘
                    │
                    ▼ 统一跳转
┌─ 任务工作流（/video-tasks/{id}/...）────────────┐
│ 素材补充与确认 → AI 分析/参考视频解析 →          │
│ 方案选择 → 分镜 → 关键帧 → 视频片段 → 成片审核   │
└────────────────────────────────────────────────┘
```

**`/products/new` 不负责完整素材管理。** 它只收集"创建任务所需的最小输入"。创建成功后，所有素材补充、检查、确认都在 `/assets` 页面完成。

### 2.1 4 种 taskMode 概述

| taskMode | 标题 | 含义 | 创建页最小输入 | 任务工作流页数 |
|---|---|---|---|---|
| `PRODUCT_CREATIVE` | AI 根据商品生成创意 | 从商品图和卖点出发，AI 全自动生成 | 商品名称 + 至少 1 张商品图 | 7 页 |
| `REFERENCE_STORYBOARD` | 按参考视频分镜生成 | 上传参考视频，AI 拆解分镜结构再替换成用户商品 | 商品名称 + 参考视频 URL | **8 页** |
| `USER_SCRIPT` | 我有脚本 | 用户提供完整脚本文本，AI 拆解分镜 | 商品名称 + 脚本文本 | 7 页 |
| `CUSTOM_STORYBOARD` | 我有分镜 | 用户提供分镜结构，AI 补齐画面和片段 | 商品名称 + 分镜内容 | 7 页 |

### 2.2 V1 硬性规则：确认进入 AI 前必须至少有 1 张商品图

服装类视频没有商品图，后面关键帧和视频片段很容易跑偏。因此：

| 模式 | 创建页 Step 2 商品图 | `/assets` 页确认前要求 |
|---|---|---|
| `PRODUCT_CREATIVE` | **必填**，至少 1 张 | 已满足（创建页已上传） |
| `REFERENCE_STORYBOARD` | 可选 | **必须补至少 1 张商品图** |
| `USER_SCRIPT` | 可选 | **必须补至少 1 张商品图** |
| `CUSTOM_STORYBOARD` | 可选 | **必须补至少 1 张商品图** |

**4 种模式的差异体现在两处：**
1. **用户链路** — 只有 `REFERENCE_STORYBOARD` 多一个"参考视频分析"页面，其余 3 种模式页面顺序完全相同
2. **传给 AI 的数据** — 不同模式的用户输入保存在 `creative_state.userRequirements`，影响 AI 生成的内容

---

## 3. 创建任务入口（`/products/new`）

`/products/new` 是任务创建向导。它只负责完成"创建任务所需的最小输入"，**不承担完整素材管理职责**。

### 3.1 创建向导的两个 Step

```
┌─ Step 1 ──────────────────────────────────────┐
│ 选择创作方式 taskMode                            │
│ PRODUCT_CREATIVE / REFERENCE_STORYBOARD /       │
│ USER_SCRIPT / CUSTOM_STORYBOARD                 │
└────────────────────────────────────────────────┘
                    │
                    ▼
┌─ Step 2 ──────────────────────────────────────┐
│ 填写该 taskMode 的「最小创建信息」               │
│                                                 │
│ 注意：这里不是完整素材上传页。                    │
│ 创建页带入的素材在 /assets 中展示为"已带入素材"。 │
│ 用户可在 /assets 继续补充缺失素材。               │
└────────────────────────────────────────────────┘
                    │
                    ▼ 点击"创建任务"
┌─ 后端 ────────────────────────────────────────┐
│ 创建 product + video_task + creative_state      │
│ + 初始 task_assets（如有）                       │
│ 返回 taskId + redirectUrl                       │
└────────────────────────────────────────────────┘
                    │
                    ▼ 统一跳转
         /video-tasks/{taskId}/assets
```

**关键约定：** Step 2 只收集"最小创建信息"。`/assets` 才是创建成功后的素材补充与确认页。两者职责不同，不可混用。

### 3.2 各 taskMode 的最小创建信息

| 字段 | PRODUCT_CREATIVE | REFERENCE_STORYBOARD | USER_SCRIPT | CUSTOM_STORYBOARD |
|---|---|---|---|---|
| 商品名称 | **必填** | **必填** | **必填** | **必填** |
| 商品描述 | 可选 | 可选 | 可选 | 可选 |
| 商品图片 | **必填，至少 1 张** | 可选（后续 `/assets` 页可补，但确认前必须至少有 1 张） | 可选（同上） | 可选（同上） |
| 参考视频 URL | — | **必填** | — | — |
| 脚本内容 | — | — | **必填** | — |
| 分镜内容 | — | — | — | **必填** |
| 目标市场 | 必填，默认 US | 必填，默认 US | 必填，默认 US | 必填，默认 US |
| 语言 | 必填，默认 en | 必填，默认 en | 必填，默认 en | 必填，默认 en |
| 视频时长 | 必填，默认 20s | 必填，默认 20s | 必填，默认 20s | 必填，默认 20s |
| 生成字幕 | 可选，默认 true | 可选，默认 true | 可选，默认 true | 可选，默认 true |

### 3.3 提交创建：推荐聚合接口

当前前端顺序调用 3 个 API（create product → create task → saveInitialContext），存在中间失败风险：product 和 task 创建成功但 `saveInitialContext` 失败，导致参考视频/脚本/分镜丢失。

**推荐改为单次聚合请求：**

```http
POST /api/fashion-video-tasks
```

请求体：

```json
{
  "taskMode": "REFERENCE_STORYBOARD",
  "product": {
    "name": "法式印花茶歇裙",
    "description": "A-line floral dress",
    "imageUrls": ["https://cos.xxx.com/dress-front.jpg"]
  },
  "initialInput": {
    "referenceVideoUrl": "https://cos.xxx.com/ref.mp4",
    "scriptText": null,
    "storyboardText": null
  },
  "settings": {
    "targetMarket": "US",
    "language": "en",
    "duration": 20,
    "needSubtitles": true
  }
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "taskId": "uuid-001",
    "productId": "uuid-prod-001",
    "status": "asset_uploading",
    "redirectUrl": "/video-tasks/uuid-001/assets"
  }
}
```

前端只需：

```typescript
const res = await createFashionVideoTask(formData);
router.push(res.data.redirectUrl);
```

不会出现三个接口之间的中间失败状态。

**向后兼容：** 在聚合接口落地前，当前分步调用也能工作，但实现时必须确保 `saveInitialContext` 失败时前端能感知并提示用户。

### 3.4 创建后发生了什么

1. 后端创建 product、video_task（初始状态 `asset_uploading`）、creative_state
2. 如果在 Step 2 上传了商品图，后端将其写入 `task_assets`（`assetRole: product_front`）
3. 如果是 `REFERENCE_STORYBOARD`，后端将参考视频 URL 写入 `task_assets`（`assetRole: reference_video`）
4. 后端返回 `redirectUrl`，前端跳转到 `/video-tasks/{taskId}/assets`
5. `/assets` 页面加载时，从 `GET /api/video-tasks/{taskId}/assets` 获取已有素材列表
6. **创建页已带入的素材（商品图、参考视频）会在 `/assets` 中以"已带入素材"展示，不是空列表**
7. 用户在 `/assets` 补充缺失素材、确认所有素材无误后，点击确认才会触发 AI 分析

### 3.5 创建页的图片上传不等于 `/assets` 的素材管理

| 概念 | `/products/new` Step 2 | `/video-tasks/{id}/assets` |
|---|---|---|
| 定位 | 创建任务最小入口 | 创建后完整素材补充与确认 |
| 什么时候用 | 仅创建时用一次 | 任务全生命周期可用（素材阶段） |
| 图片上传 | 仅收集必要商品图 | 支持全部 assetRole（商品图/模特/场景/穿搭等） |
| 是否触发 AI | 不触发 | 用户点确认后触发 |
| 能否修改 | 创建后不可回退修改 | 在 `asset_uploading` 和 `waiting_asset_confirmation` 期间可随时增删改 |

---

## 4. 完整用户链路（按模式）

### 4.1 核心状态机路径

```
asset_uploading → asset_analyzing → waiting_asset_confirmation
                                          │
                    ┌─────────────────────┤
                    │ 有 reference_video   │ 无 reference_video
                    ▼  asset?              ▼
              reference_analyzing    plan_generating
                    │                     │
                    ▼                     ▼
              plan_generating      waiting_plan_selection
                    │                     │
                    └─────────┬───────────┘
                              ▼
                    storyboard_generating
                              │
                              ▼
                    waiting_storyboard_confirmation
                              │
                              ▼
                    keyframe_configuring
                         │          │
                         ▼          ▼
                  image_generating  waiting_image_confirmation
                         │          │
                         └────┬─────┘
                              ▼
                    video_clip_generating
                              │
                              ▼
                    waiting_video_clip_confirmation
                              │
                              ▼
                          rendering
                              │
                              ▼
                    waiting_final_review
                         │          │
                         ▼          ▼
                     completed   repairing → (回到上游节点重做)
```

**关键分叉点：** `waiting_asset_confirmation` 之后，后端检查是否存在 `reference_video` 资产：

```java
// TaskAssetServiceImpl.java:167-176
private String determineNextStatus(VideoTaskEntity task) {
    List<TaskAssetEntity> refVideos = taskAssetMapper.findByTaskId(task.getId()).stream()
            .filter(a -> "reference_video".equals(a.getAssetRole()))
            .collect(Collectors.toList());
    if (!refVideos.isEmpty()) {
        return "reference_analyzing";  // → 仅 REFERENCE_STORYBOARD 走这里
    }
    return "plan_generating";          // → 其他 3 种模式走这里
}
```

---

### 4.2 模式 1：`PRODUCT_CREATIVE` — AI 根据商品生成创意

**页面链路（7 页）：**

```
[素材管理] → [方案选择] → [进度页过渡] → [分镜编辑] → [关键帧配置] → [视频片段] → [成片审核] → ✅ 完成
```

**逐步骤详解：**

| # | 页面 | 后端状态 | 用户看到什么 | 用户操作 |
|---|---|---|---|---|
| 1 | `/assets` | `asset_uploading` | **创建页已上传的商品图自动展示为"已带入素材"** + 可继续补充模特参考、场景参考等 | 检查已带入素材，补充缺失素材 |
| 2 | `/assets` | `asset_uploading` | 素材卡片网格 + "确认商品素材并开始 AI 分析"按钮 | 点确认 → 后端推进到 `asset_analyzing` |
| 3 | `/assets` | `asset_analyzing` | 旋转动画 + "AI 正在分析你的商品素材" | 等待（5s 轮询） |
| 4 | `/assets` | `waiting_asset_confirmation` | AI 分析完成提示 + 可继续补充素材 | 再次点确认 → 后端检测无 reference_video → `plan_generating` → 前端跳转 `/plans` |
| 5 | `/plans` | `plan_generating` | 旋转动画 + "AI 正在分析商品并生成视频方案..." | 等待 |
| 6 | `/plans` | `waiting_plan_selection` | 2-3 个方案卡片（标题/hook/结构/评分/推荐理由） | 选一个方案点"选择此方案" |
| 7 | `/progress` | `storyboard_generating` | 进度条 + "查看分镜脚本 →"链接 | 等待，点链接进分镜页 |
| 8 | `/storyboard` | `waiting_storyboard_confirmation` | 分镜脚本（标题/hook/字幕/标签/镜头列表）全部可编辑 | 编辑 → 保存 → "确认分镜，进入关键帧" |
| 9 | `/keyframes` | `keyframe_configuring` | 逐镜头卡片：镜头信息 + 未配置徽章 | 上传图片 或 AI 生成 |
| 10 | `/keyframes` | `image_generating` | 全屏旋转动画 + "AI 正在生成关键帧图片" | 等待 |
| 11 | `/keyframes` | `waiting_image_confirmation` | 逐镜头卡片：图片预览 + 确认/驳回按钮 | 逐镜头确认，全部确认后自动跳转 |
| 12 | `/clips` | `video_clip_generating` | 全屏旋转动画 + "AI 正在生成视频片段" | 等待 |
| 13 | `/clips` | `waiting_video_clip_confirmation` | 逐镜头卡片：视频预览 + 确认/驳回按钮 | 逐镜头确认，全确认后点"开始渲染" |
| 14 | `/review` | `rendering` | 旋转动画 + "正在渲染成片" | 等待 |
| 15 | `/review` | `waiting_final_review` | 视频播放器 + "批准成片"/"请求修复"按钮 | 预览 → 批准通过 |
| 16 | — | `completed` | 绿色通过 + 下载链接 | 下载或导出 |

---

### 4.3 模式 2：`REFERENCE_STORYBOARD` — 按参考视频分镜生成

**页面链路（8 页，唯一多 1 页的模式）：**

```
[素材管理] → [参考视频分析] → [方案选择] → [进度页] → [分镜编辑] → [关键帧配置] → [视频片段] → [成片审核] → ✅
```

**与模式 1 的核心差异：**

| 步骤 | 模式 1 | 模式 2 |
|---|---|---|
| 创建时 | 上传商品图 | 上传参考视频 URL（自动写入 `reference_video` asset） |
| 进入 `/assets` 时 | 已带入商品图 | **已带入参考视频**（显示在"已带入素材"区域），商品图区域为空，需补充 |
| 素材确认后 | → `plan_generating` | → `reference_analyzing` |
| 前端跳转 | → `/plans` | → `/reference-analysis` |
| AI 后续 | 直接生成方案 | 先分析参考视频结构 → 再生成方案 |

**`/reference-analysis` 页面展示内容：**
- 3 张统计卡片：总时长、镜头数、结构模式（Hook → Show → Detail → CTA）
- 逐镜头拆解卡片：shotNo、时间段、场景、动作、机位、转场、字幕、结构角色
- AI 回调 `reference_analysis` 完成后 → 状态自动推进到 `plan_generating` → 页面显示"进入方案选择 →"链接
- 后续流程与模式 1 完全相同

---

### 4.4 模式 3：`USER_SCRIPT` — 我有脚本

**页面链路（7 页，与模式 1 完全相同）：**

```
[素材管理] → [方案选择] → [进度页] → [分镜编辑] → [关键帧配置] → [视频片段] → [成片审核] → ✅
```

**与模式 1 的差异不在页面顺序上：**
- 进入 `/assets` 时，区域 1 显示"已保存脚本"摘要（前 100 字），区域 2 为空（创建页未传商品图）
- 用户必须在 `/assets` 补充至少 1 张商品图，否则不能点确认
- `creative_state.userRequirements.scriptText` 包含用户完整脚本
- AI 编排器生成方案和分镜时以脚本文本为核心输入
- 用户仍可在分镜编辑页对 AI 拆解结果修改

---

### 4.5 模式 4：`CUSTOM_STORYBOARD` — 我有分镜

**页面链路（7 页，与模式 1 完全相同）：**

```
[素材管理] → [方案选择] → [进度页] → [分镜编辑] → [关键帧配置] → [视频片段] → [成片审核] → ✅
```

**与模式 1 的差异不在页面顺序上：**
- 进入 `/assets` 时，区域 1 显示"已保存分镜"摘要（前 100 字），区域 2 为空（创建页未传商品图）
- 用户必须在 `/assets` 补充至少 1 张商品图，否则不能点确认
- `creative_state.userRequirements.storyboardText` 包含用户分镜结构
- AI 编排器将分镜内容作为约束条件，补齐 prompt 和素材
- 用户可在分镜编辑页微调 AI 补全的结果

---

## 5. 各前端页面详细行为

### 5.1 素材管理页（`/assets`）

**文件：** `apps/web/src/app/video-tasks/[id]/assets/page.tsx`
**适用状态：** `asset_uploading` / `asset_analyzing` / `waiting_asset_confirmation`
**轮询：** 每 5 秒
**定位：** 创建任务后的第一个工作页，负责素材展示、补充、确认。不是"第二步表单"。

#### 页面布局（三分区）

```
┌─ 区域 1：创建页已带入的信息（只读摘要）────────────┐
│ taskMode: PRODUCT_CREATIVE                           │
│ 商品名称: 法式印花茶歇裙                               │
│ 商品描述: A-line floral dress...                      │
│ 脚本/分镜摘要: —（该模式无）                           │
└──────────────────────────────────────────────────────┘

┌─ 区域 2：已带入素材（来自创建页 Step 2）────────────┐
│ ┌──────────┐ ┌──────────┐                            │
│ │ 商品正面  │ │ 商品背面  │                            │
│ │ [缩略图]  │ │ [缩略图]  │                            │
│ └──────────┘ └──────────┘                            │
│ (如果创建页没传图，这里显示"暂无从创建页带入的素材")     │
└──────────────────────────────────────────────────────┘

┌─ 区域 3：补充素材 ─────────────────────────────────┐
│ [+ 添加素材] 按钮 → 展开上传表单                       │
│                                                      │
│ 素材卡片网格（用户后续上传的 + 区域 2 的合集）:         │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│ │ 模特参考  │ │ 场景参考  │ │ 穿搭参考  │              │
│ │ [缩略图]  │ │ [缩略图]  │ │ [缩略图]  │              │
│ └──────────┘ └──────────┘ └──────────┘              │
│                                                      │
│ [确认素材并开始 AI 分析] 按钮（需至少 1 张商品图）       │
└──────────────────────────────────────────────────────┘
```

#### 按 taskMode 差异化

**`PRODUCT_CREATIVE`：**

- 页面标题：**确认商品素材**
- 说明：`AI 会根据商品图片和商品描述生成视频创意。请确认至少有 1 张商品图片，建议补充正面、背面、细节图。`
- 已带入素材：创建页上传的商品图
- 必填素材（确认前）：至少 1 张商品图（→ 创建页已满足）
- 主按钮文案：**确认商品素材并开始 AI 分析**

**`REFERENCE_STORYBOARD`：**

- 页面标题：**确认参考视频与商品素材**
- 说明：`系统已带入你在创建页填写的参考视频。请补充至少 1 张商品图。确认后，AI 会先拆解参考视频分镜。`
- 已带入素材：**参考视频**（`reference_video`，显示视频缩略图或链接）
- 必填素材（确认前）：至少 1 张商品图（→ 需用户在 `/assets` 补充）
- 主按钮文案：**确认素材并分析参考视频**

**`USER_SCRIPT`：**

- 页面标题：**确认脚本与商品素材**
- 说明：`系统已保存你提供的脚本。请补充至少 1 张商品图，便于后续生成关键帧和视频片段。`
- 已带入信息：脚本摘要（区域 1 显示前 100 字）
- 必填素材（确认前）：至少 1 张商品图（→ 需用户在 `/assets` 补充）
- 主按钮文案：**确认素材并生成视频方案**

**`CUSTOM_STORYBOARD`：**

- 页面标题：**确认分镜与商品素材**
- 说明：`系统已保存你提供的分镜结构。请补充至少 1 张商品图，便于 AI 补齐画面提示词和关键帧。`
- 已带入信息：分镜摘要（区域 1 显示前 100 字）
- 必填素材（确认前）：至少 1 张商品图（→ 需用户在 `/assets` 补充）
- 主按钮文案：**确认素材并生成视频方案**

#### 状态驱动的 UI 行为

```
asset_uploading:
  → 区域 1 显示任务摘要（taskMode + 商品信息 + 脚本/分镜摘要）
  → 区域 2 显示创建页已带入的初始素材
  → 区域 3 可继续添加素材
  → 主按钮："确认素材并开始 AI 分析"（文案按模式不同）
  → 确认条件：至少 1 张商品图（任意 assetRole 含 "product_" 前缀）
  → 点确认 → POST /api/video-tasks/{id}/assets/confirm
              → 后端推进到 asset_analyzing

asset_analyzing:
  → 全屏旋转动画 + "AI 正在分析你的商品素材"
  → "分析商品属性、风格、材质、推荐拍摄角度…"
  → 不可编辑素材

waiting_asset_confirmation:
  → AI 分析完成后，素材卡片恢复可编辑
  → 蓝色提示条："AI 分析已完成。如需调整素材可继续添加。"
  → 再次点确认 → 后端 determineNextStatus() → 前端路由分叉:
      nextStatus === "reference_analyzing" → /reference-analysis
      nextStatus === "plan_generating" / "waiting_plan_selection" → /plans
      其他 → 留在当前页继续轮询
```

#### 资产角色（assetRole）完整列表

| 角色 | assetRole | 说明 |
|---|---|---|
| 商品正面 | `product_front` | 商品正面图 |
| 商品背面 | `product_back` | 商品背面图 |
| 商品细节 | `product_detail` | 材质、纹理、标签特写 |
| 模特参考 | `model_reference` | 模特穿搭效果 |
| 场景参考 | `scene_reference` | 拍摄场景、背景参考 |
| 穿搭参考 | `outfit_reference` | 搭配方式参考 |
| 参考视频 | `reference_video` | REFERENCE_STORYBOARD 模式的核心输入 |
| 用户关键帧 | `user_keyframe` | 用户手动上传的关键帧 |
| AI 关键帧 | `ai_keyframe` | AI 生成的关键帧 |
| 图片变体 | `image_variant` | AI 生成的图片候选 |
| 视频片段 | `video_clip` | AI 生成或用户上传的视频片段 |
| 最终成片 | `final_video` | 渲染完成的 MP4 |
| 封面图 | `cover_image` | 视频封面缩略图 |

---

### 5.2 参考视频分析页（`/reference-analysis`）

**文件：** `apps/web/src/app/video-tasks/[id]/reference-analysis/page.tsx`
**适用状态：** `reference_analyzing`（只读展示页）

**UI 行为：**
- 从 `GET /api/video-tasks/{id}/creative-state` 获取 `referenceVideo` 分析结果
- 无参考视频时：显示空状态 + 返回素材管理链接
- 有参考视频时：
  - 3 张统计卡片：总时长（s）、镜头数、结构模式（如 Hook → Show → Detail → CTA）
  - 逐镜头卡片列表：shotNo、时间段（startTime-endTime）、场景、动作、机位、转场、字幕、结构角色
- 状态变为 `plan_generating` / `waiting_plan_selection` 后：显示蓝色提示条 + "进入方案选择 →" 链接

---

### 5.3 方案选择页（`/plans`）

**文件：** `apps/web/src/app/video-tasks/[id]/plans/page.tsx`
**适用状态：** `plan_generating` / `waiting_plan_selection`（也兼容旧 V1 状态）
**轮询：** 每 5 秒

**UI 状态机：**

```
plan_generating / analyzing / analysis_completed / plan_generated:
  → 旋转动画 + "AI 正在分析商品并生成视频方案...（预计30-60秒）"

waiting_plan_selection + plans.length > 0:
  → 方案卡片列表，每张卡片包含:
      - 标题 (plan.title)
      - Hook (plan.hook)
      - 结构标签 (plan.structure 按 "→" 拆分，如 [Hook] [Show] [Detail] [CTA])
      - 评分徽章 (plan.score 分)
      - 推荐理由 (plan.reason)
      - "选择此方案"按钮

waiting_plan_selection + plans.length === 0:
  → "方案暂未返回，页面会继续自动刷新"

选择方案后:
  POST /api/video-tasks/{id}/confirm-plan  { planId }
  → router.push('/progress')  // storyboard_generating 阶段
```

---

### 5.4 分镜编辑页（`/storyboard`）

**文件：** `apps/web/src/app/video-tasks/[id]/storyboard/page.tsx`
**适用状态：** `waiting_storyboard_confirmation`

**UI 行为：**
- 顶部提示条（仅 waiting 状态）："AI 分镜已生成。请检查并编辑分镜内容，确认无误后点击「确认分镜」进入关键帧配置。"
- **可编辑字段：** 标题、Hook（开场）、封面文案、字幕/文案、标签（逗号分隔）
- **只读预览：** 完整脚本、音乐建议
- **镜头列表（只读）：** 每个镜头显示 shotNo、时长、场景、动作、字幕、素材类型徽章、编辑指令、AI Prompt
- **操作：** 保存修改（PATCH）/ 确认分镜进入关键帧（POST confirm → router.push keyframes）

---

### 5.5 关键帧配置页（`/keyframes`）★ 核心页面

**文件：** `apps/web/src/app/video-tasks/[id]/keyframes/page.tsx`
**适用状态：** `keyframe_configuring` / `image_generating` / `waiting_image_confirmation`
**轮询：** 每 5 秒
**数据加载：** 同时请求 3 个 API（task + keyframes + storyboard）

**整体页面状态：**

```
image_generating:
  → 全屏旋转动画 + "AI 正在生成关键帧图片"
  → 提示: "根据分镜脚本和素材风格生成每个镜头的关键帧…"

其他状态:
  → 逐镜头卡片列表
  → 全确认时顶部显示绿色按钮"全部已确认，进入片段 →"
```

**逐镜头卡片渲染逻辑：**

```
每个 storyboard shot 渲染一张卡片:

┌──────────────────────────────────────────────────────────┐
│ 镜头 N · Xs                            [状态徽章]        │
│ 场景描述 (shot.scene)                                    │
│ 字幕文案 (shot.subtitle)                                 │
│                                                          │
│ ┌──────────────────────────────────────────────────────┐ │
│ │              关键帧图片预览 (kf.url)                  │ │
│ │              h-40 object-cover                        │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ [生成中动画] (仅 kf.status === "generating")              │
│                                                          │
│ [上传图片] [AI 生成]         或         [确认] [驳回]      │
│                                                          │
│ ┌─ 展开操作面板 ───────────────────────────────────────┐ │
│ │ [上传图片] / [AI 生成]  tab切换                       │ │
│ │ 用途选择: [首帧▼] [末帧] [参考] [商品细节]            │ │
│ │                                                      │ │
│ │ 上传模式: URL输入框 + [确认上传]                       │ │
│ │ AI 模式:  Prompt多行输入(预填shot.prompt) + [请求AI生成]│ │
│ └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

**状态徽章与操作按钮对照表：**

| kf 状态 | 徽章样式 | 卡片边框 | 操作按钮 |
|---|---|---|---|
| 无 kf | 灰色"未配置" | 默认 | [上传图片] [AI 生成] |
| `draft` | 琥珀色"待确认" | 默认 | [上传图片] [AI 生成] |
| `generating` | 旋转动画"AI 生成中…" | 默认 | — |
| `uploaded` | 琥珀色"待确认" | 默认 | [确认] [驳回] |
| `generated` | 琥珀色"待确认" | 默认 | [确认] [驳回] |
| `confirmed` | 绿色"已确认" | 绿色边框 | — |
| `rejected` | 红色"已驳回" | 红色边框 | [上传图片] [AI 生成] |
| `failed` | — | — | [上传图片] [AI 生成]（需 regenerate） |

**确认/驳回逻辑：**

```typescript
// 确认单个关键帧
POST /api/video-tasks/{id}/keyframes/{kfId}/confirm  { confirmed: true }
→ 后端: kf.status = "confirmed"
→ 后端检查是否全部确认 → 是 → task: waiting_image_confirmation → video_clip_generating
→ 前端: 如果 res.data.status === "video_clip_generating" → router.push('/clips')
→ 否则: load() 刷新列表

// 驳回单个关键帧
POST /api/video-tasks/{id}/keyframes/{kfId}/reject  { confirmed: false }
→ 后端: kf.status = "rejected"（task 状态不变）
→ 前端: load() 刷新 → 按钮变回"上传图片 / AI 生成"
```

**全部确认检测：**

```typescript
const allConfirmed = shots.length > 0 && shots.every(s => {
  const kf = getKeyframeForShot(s.shotNo ?? 0);
  return kf?.status === "confirmed";
});
// 全部确认 + task.status 可操作 → 显示绿色按钮"全部已确认，进入片段 →"
```

**与后端 KeyframeServiceImpl 的对应关系：**

| 前端操作 | 后端方法 | kf.status 变化 | task.status 变化 |
|---|---|---|---|
| 上传图片 | `addKeyframe(source="user_upload")` | → `uploaded` | → `waiting_image_confirmation` |
| AI 生成 | `addKeyframe(source="ai_generated")` | → `draft` | → `image_generating` |
| AI 回调 | callback `keyframe` stage | → `generated` | → `waiting_image_confirmation` |
| 确认 | `confirmKeyframe(confirmed=true)` | → `confirmed` | 全确认 → `video_clip_generating` |
| 驳回 | `rejectKeyframe()` | → `rejected` | 不变 |
| 批量 AI 生成 | `generateKeyframes()` | 批量 → `draft` | → `image_generating` |
| 重生成 | `regenerateKeyframe()` | → `generating` | → `image_generating` |

---

### 5.6 视频片段页（`/clips`）

**文件：** `apps/web/src/app/video-tasks/[id]/clips/page.tsx`
**适用状态：** `video_clip_generating` / `waiting_video_clip_confirmation`
**轮询：** 每 5 秒

**UI 行为（与关键帧页模式相同）：**

```
video_clip_generating:
  → 全屏旋转动画 + "AI 正在生成视频片段"
  → "基于已确认的关键帧和分镜脚本生成每个镜头的短视频…"

waiting_video_clip_confirmation:
  → 逐镜头卡片:
      - 镜头 N + 时长 + 来源(AI生成/用户上传)
      - 状态徽章: 生成中/待确认/已确认/已驳回
      - <video> 播放器预览 (clip.url)
      - Prompt 展示
      - 操作: [确认] [驳回]
  → 全确认后: "开始渲染成片"按钮
      POST /api/video-tasks/{id}/render
      → router.push('/review')
```

---

### 5.7 成片审核页（`/review`）

**文件：** `apps/web/src/app/video-tasks/[id]/review/page.tsx`
**适用状态：** `rendering` / `waiting_final_review` / `repairing` / `completed`
**轮询：** 每 5 秒

**UI 状态机：**

```
rendering:
  → 旋转动画 + "正在渲染成片"
  → "正在将所有已确认片段合成为最终视频…"

waiting_final_review:
  → 视频播放器 (<video> 标签)
  → 视频信息: 标题/时长/分辨率/质量分
  → [批准成片] 按钮 (绿色) + [请求修复] 按钮 (琥珀色)
  → [下载视频] 链接

repairing:
  → 扳手动画 + "修复进行中"
  → "AI 正在根据你的反馈修复视频…"
  → 修复历史列表（repairEvents）

completed:
  → 绿色大对勾 + "审核通过"
  → "视频已完成，可以下载或导出到 TikTok Shop"
```

**反馈修复表单：**

```
展开后:
  问题类型: [画质问题▼] [商品不准确] [光线问题] [动作僵硬] [细节缺失] [构图问题] [风格不匹配] [其他]
  修复目标: [分镜▼] [关键帧] [视频片段] [渲染清单] [成片]
  详细描述: [多行文本]
  [取消] [提交反馈]

提交 → POST /api/video-tasks/{id}/feedback
→ task: waiting_final_review → repairing
→ AI repair workflow 完成后 → 回到对应确认节点
```

---

### 5.8 进度页（`/progress`）

**文件：** `apps/web/src/app/video-tasks/[id]/progress/page.tsx`
**适用状态：** 所有状态（通用查看页）

**UI 行为：**
- 任务标题 + 状态徽章 + 进度百分比
- 进度条组件（TaskProgress）
- **根据当前状态显示操作链接卡片：**

| 状态 | 链接卡片 |
|---|---|
| `asset_uploading` / `asset_analyzing` / `waiting_asset_confirmation` | 📷 管理素材 → |
| `keyframe_configuring` / `image_generating` / `waiting_image_confirmation` | 🖼️ 管理关键帧 → |
| `video_clip_generating` / `waiting_video_clip_confirmation` | 🎬 管理视频片段 → |
| `waiting_final_review` / `repairing` / `rendering` | ✅ 查看成片审核 → |
| `completed` + 有 videoId | 🎬 视频生成完成 → 查看成片 |
| `failed` | 错误信息 + [重新尝试] 按钮 |

---

### 5.9 工作台（`/dashboard`）

**文件：** `apps/web/src/app/dashboard/page.tsx`

**核心职责：** 根据 `task.status` 将用户路由到正确的操作页面。

**导航规则（`getTaskHref` 函数）：**

| task.status | 点击跳转 | 卡片样式 |
|---|---|---|
| `asset_uploading` / `asset_analyzing` / `waiting_asset_confirmation` | `/video-tasks/{id}/assets` | 琥珀色（等待操作） |
| `reference_analyzing` | `/video-tasks/{id}/reference-analysis` | 蓝色（进行中） |
| `waiting_plan_selection` / `plan_generating` | `/video-tasks/{id}/plans` | 琥珀色（等待操作） |
| `storyboard_generating` | `/video-tasks/{id}/progress` | 蓝色 + 进度条 |
| `waiting_storyboard_confirmation` | `/video-tasks/{id}/storyboard` | 琥珀色 |
| `keyframe_configuring` / `image_generating` / `waiting_image_confirmation` | `/video-tasks/{id}/keyframes` | 琥珀色 |
| `video_clip_generating` / `waiting_video_clip_confirmation` | `/video-tasks/{id}/clips` | 琥珀色 |
| `waiting_final_review` / `repairing` / `rendering` | `/video-tasks/{id}/review` | 琥珀色 |
| `completed` / `exported` / `failed` / `cancelled` | `/video-tasks/{id}/progress` | 绿/红/灰 |

**`NEEDS_ACTION_STATES`（需要用户操作的等待状态）：**
```typescript
["waiting_plan_selection", "waiting_asset_confirmation",
 "waiting_storyboard_confirmation", "waiting_image_confirmation",
 "waiting_video_clip_confirmation", "waiting_final_review"]
```
这些状态的卡片显示琥珀色边框 + "待操作"徽章。

---

## 6. 状态驱动的页面跳转规则

前端**不自行判断**下一步去哪。所有跳转都由**后端返回的状态**决定：

| 触发事件 | 后端返回的状态 | 前端跳转目标 |
|---|---|---|
| 创建任务 | `asset_uploading` | → `/assets` |
| 第一次确认素材 | `asset_analyzing` | 留在 `/assets`（轮询） |
| 第二次确认素材（无 reference_video） | `plan_generating` / `waiting_plan_selection` | → `/plans` |
| 第二次确认素材（有 reference_video） | `reference_analyzing` | → `/reference-analysis` |
| AI 参考分析回调 | `plan_generating` | 留在 `/reference-analysis`（显示提示链接） |
| AI 方案生成回调 | `waiting_plan_selection` | 留在 `/plans`（显示方案卡片） |
| 用户选择方案 | `storyboard_generating` | → `/progress` |
| AI 分镜生成回调 | `waiting_storyboard_confirmation` | 留在 `/progress`（显示分镜链接） |
| 用户确认分镜 | `keyframe_configuring` | → `/keyframes` |
| 用户上传关键帧 | `waiting_image_confirmation` | 留在 `/keyframes` |
| 用户请求 AI 生成关键帧 | `image_generating` | 留在 `/keyframes` |
| AI 关键帧回调 | `waiting_image_confirmation` | 留在 `/keyframes` |
| 用户确认关键帧（全部确认） | `video_clip_generating` | → `/clips` |
| 用户确认视频片段（全部确认） | `rendering` | → `/review` |
| 渲染完成回调 | `waiting_final_review` | 留在 `/review`（显示视频） |
| 用户批准成片 | `completed` | 留在 `/review`（显示成功） |
| 用户提交反馈 | `repairing` | 留在 `/review`（显示修复中） |
| AI 修复回调 | 对应上游节点状态 | 留在 `/review` 或跳转 |

---

## 7. 前端通用模式总结

1. **每 5 秒轮询** — 所有工作流页面（assets/keyframes/clips/review/plans/progress）都用 `setInterval(load, 5000)`
2. **状态驱动 UI** — 按钮、徽章、动画、卡片边框全部由 `task.status` 和子对象 `.status` 决定
3. **后端决定路由** — 前端不自行判断"下一步去哪"，而是根据 API 返回的 `status` 字段跳转
4. **乐观跳转** — 确认操作后如果后端返回状态已推进到下一阶段，前端直接 `router.push`
5. **卡片式布局** — 逐镜头（per-shot）展示，每张卡片包含：信息 + 预览 + 状态 + 操作
6. **展开式操作面板** — 上传/AI 生成不跳转页面，在当前卡片内展开内联表单
7. **`taskMode` 存储在 `VideoTask.taskMode`** — 前端可随时读取，当前主要用于创建时的差异化处理
8. **所有 AI 触发都是 fire-and-forget** — Java 调 Python 后不等结果，AI 完成通过 callback 回写状态，前端靠轮询感知变化

---

## 8. 页面职责与边界

| 页面 | 职责 | 不应该做的事 |
|---|---|---|
| `/products/new` | 选择创作方式，收集当前模式的最小必要输入 | 不展示 `videoType`，不要求所有模式都先上传商品图片 |
| `/video-tasks/[id]/assets` | 管理商品图、参考视频、模特图、场景图等素材角色 | 不直接推进 AI 状态，状态仍由 Java 后端控制 |
| `/video-tasks/[id]/reference-analysis` | 展示参考视频拆解结果（只读） | 不替代方案选择页 |
| `/video-tasks/[id]/plans` | 展示 AI 生成的创意方案，供用户选择 | 不再承担素材上传职责 |
| `/video-tasks/[id]/storyboard` | 用户确认或微调分镜和脚本 | 不重新创建商品 |
| `/video-tasks/[id]/keyframes` | 用户为每个镜头配置关键帧（上传/AI生成/确认/驳回） | 不直接进入昂贵视频生成，必须先确认 |
| `/video-tasks/[id]/clips` | 用户预览和确认视频片段 | 不直接渲染成片，必须先确认 |
| `/video-tasks/[id]/review` | 成片预览、反馈、局部修复 | 不整条重跑，优先局部 repair |
| `/video-tasks/[id]/progress` | 任何状态的通用进度查看 | 不替代具体操作页面 |

---

## 9. 数据流向

```
用户输入（脚本/分镜/风格要求）
  → creative_state.userRequirements (JSONB)
  → Python AI Orchestrator 读取
  → AI 生成结果
  → callback → Java 写入对应表（video_plans/storyboards/keyframes/video_clips）
  → 前端轮询 GET API 获取最新状态
```

```
用户上传的素材文件（图片/视频）
  → task_assets (assetKind + assetRole + url)
  → Python AI Orchestrator 读取素材列表
  → AI 分析/生成
  → callback → Java 写入结果
```

---

## 10. 后续开发规则

1. 新增创作入口时，优先扩展 `taskMode`，不要新增一个并列的"视频类型选择器"。
2. 高成本视频生成必须放在用户确认关键帧之后。
3. 用户上传的参考视频、商品图、模特图、场景图必须进 `task_assets`，不要散落在页面局部 state。
4. 用户的脚本、分镜、风格要求必须进 `creative_state.userRequirements`。
5. AI repair 必须回到具体问题环节，例如 keyframe、video_clip、render，而不是整条链路重跑。
6. 如果某个字段要跨前端、Java、Python 使用，必须同步检查 OpenAPI、数据库契约、Pydantic Schema 和运行时检查脚本。
7. **新增或修改 API 字段时，必须先改 `docs/02-openapi-spec.yaml`，再运行 `npm run generate:api-types`，最后修前端编译错误。** 绝不允许手写 TypeScript 类型来镜像 API 响应。
