# 服装 AI 短视频创意生产系统 V1.0 需求文档

## 1. 项目名称

中文名称：服装 AI 短视频创意生产系统

英文暂定名：Fashion Creative Loop Engine

## 2. 项目定位

本系统面向跨境服装商家、TikTok Shop 卖家、独立站服装品牌和内容运营团队，提供从服装素材到 AI 图片、AI 视频片段、最终短视频成片的创意生产能力。

系统不是单纯的提示词工具，也不是单纯的视频渲染工具，而是一个带人工确认节点和循环修复能力的 AI 图文视频生产工作台。

核心定位：

1. 用户可以上传商品图、模特图、场景图、参考视频、关键帧图片；
2. 系统可以分析商品、卖点、参考视频结构和用户要求；
3. 系统可以生成脚本、分镜、关键帧图片、视频片段和最终成片；
4. 用户可以在关键节点介入修改；
5. 系统可以根据用户反馈进行局部修复，而不是整条视频全部重来；
6. 系统需要控制生图、生视频成本，尤其避免不必要的视频重生成。

## 3. 产品目标

V1.0 目标是跑通一条可控的服装 AI 短视频生产链路：

```text
创建任务
↓
上传原始素材
↓
选择生成模式
↓
AI 理解商品/参考视频/用户要求
↓
生成脚本与分镜
↓
用户确认分镜
↓
配置每个镜头的关键帧
↓
AI 生图或用户上传图片
↓
用户确认关键帧图片
↓
AI 生成视频片段
↓
用户确认视频片段
↓
Render Worker 合成最终视频
↓
用户反馈
↓
局部修复循环
```

V1.0 的重点不是全自动无人值守，而是让用户在必要节点参与，从而提高质量、降低浪费、控制成本。

## 4. 目标用户

### 4.1 核心用户

1. TikTok Shop 服装卖家；
2. 跨境服装电商运营；
3. 独立站服装品牌；
4. 内容投放团队；
5. 需要批量生成服装短视频的商家。

### 4.2 典型场景

1. 用户有商品正面图、背面图，希望快速生成穿搭短视频；
2. 用户有一个爆款参考视频，希望按照它的分镜结构生成新商品视频；
3. 用户已有关键帧图片，希望直接基于图片和脚本生成视频片段；
4. 用户对 AI 生成结果不满意，希望只修复某个镜头、某张图或某个视频片段；
5. 用户需要同一商品生成多条不重复的视频，用于投放测试。

## 5. V1.0 非目标

V1.0 暂不做以下内容：

1. 不做 TikTok 自动发布；
2. 不做广告投放管理；
3. 不做复杂团队协作审批；
4. 不保证 AI 生图/生视频结果 100% 正确；
5. 不允许系统在用户不知情的情况下无限重生成昂贵视频；
6. 不把所有问题都通过整条视频重做解决；
7. 不直接用一个大模型完成所有事情。

## 6. 系统角色分工

### 6.1 Java Backend

Java Backend 是主业务状态源，负责：

1. 用户、权限、额度；
2. 视频任务状态；
3. 素材、分镜、图片、视频片段、成片记录；
4. 用户确认节点；
5. 版本管理；
6. 额度扣减与重试限制；
7. 接收 Python AI Orchestrator 和 Render Worker 回调。

### 6.2 Python AI Orchestrator

Python AI Orchestrator 负责 AI 编排，包含：

1. 商品理解；
2. 参考视频解析；
3. 用户自然语言解析；
4. 脚本生成；
5. 分镜生成；
6. Prompt 编译；
7. 生图请求；
8. 生视频请求；
9. QA 检查；
10. 局部修复决策。

### 6.3 Image Generation Provider

图片生成服务负责：

1. 根据商品约束和镜头要求生成关键帧；
2. 根据用户修改要求重生成局部图片；
3. 保持服装颜色、图案、版型、模特和场景约束。

### 6.4 Video Generation Provider

视频生成服务负责：

1. 根据关键帧图片、脚本、动作要求生成短视频片段；
2. 每个镜头独立生成；
3. 支持单镜头重生成；
4. 不直接负责最终成片拼接。

### 6.5 Node Render Worker

Render Worker 负责：

1. 下载视频片段、图片、音频、字幕等素材；
2. 使用 Remotion/FFmpeg 合成最终 MP4；
3. 生成封面；
4. 上传结果；
5. 向 Java Backend 回调渲染结果。

Render Worker 不负责创意判断，不理解用户额度，也不决定任务状态。

### 6.6 Frontend Workbench

前端工作台负责：

1. 任务创建；
2. 素材上传和角色绑定；
3. 参考视频解析结果展示；
4. 创意方案展示；
5. 分镜编辑；
6. 关键帧配置；
7. 图片确认；
8. 视频片段确认；
9. 成片预览；
10. 用户反馈和局部修复入口。

## 7. 生成模式

用户创建任务时，需要选择生成模式。

### 7.1 商品创意生成模式

适用于用户只有商品素材，希望系统自动生成创意脚本和分镜。

流程：

```text
商品素材
↓
商品理解
↓
卖点判断
↓
创意方案生成
↓
脚本与分镜生成
```

### 7.2 参考视频分镜迁移模式

适用于用户上传参考视频，并希望“按照这个视频的分镜结构”生成新商品视频。

流程：

```text
用户上传参考视频
↓
AI 拆解参考视频
↓
提取镜头数量、时长、动作、机位、节奏、爆点
↓
生成 Reference Storyboard
↓
迁移到当前商品和素材
↓
生成新脚本和分镜
```

迁移原则：

1. 可以复用镜头结构、节奏和展示逻辑；
2. 不复制原视频文案、人物身份和具体商品；
3. 当前商品的颜色、版型、正反面印花、卖点必须优先；
4. 用户可以修改解析后的参考分镜。

### 7.3 用户脚本生成模式

适用于用户已经有脚本，希望系统根据脚本生成分镜、图片和视频。

流程：

```text
用户输入脚本
↓
AI 拆解脚本
↓
生成镜头结构
↓
用户确认
↓
关键帧配置
↓
视频片段生成
```

### 7.4 自定义分镜模式

适用于用户想手动定义每个镜头。

流程：

```text
用户创建镜头
↓
填写每个镜头的动作、画面、脚本、时长
↓
系统补全 Prompt
↓
关键帧配置
↓
视频片段生成
```

## 8. 核心流程

### 8.1 主流程

```text
1. 用户创建视频任务
2. 用户选择生成模式
3. 用户上传原始素材
4. 用户绑定素材角色
5. AI 分析商品、场景、模特、参考视频和用户要求
6. AI 生成创意方案
7. 用户选择或修改创意方案
8. AI 生成脚本和分镜
9. 用户确认或修改分镜
10. 用户为每个镜头配置关键帧来源
11. 系统生成或接收关键帧图片
12. 用户确认或修改关键帧图片
13. AI 根据关键帧、脚本和动作生成视频片段
14. 用户确认或重生成视频片段
15. Render Worker 合成最终视频
16. 用户预览成片
17. 用户确认完成或进入反馈修复循环
```

### 8.2 关键原则

1. 脚本和分镜决定图片；
2. 关键帧图片约束视频；
3. 视频片段独立生成；
4. 用户反馈驱动局部修复；
5. 生视频前必须有用户确认；
6. 生图和生视频使用不同 provider、不同额度规则。

## 9. 素材体系

### 9.1 原始素材

原始素材指用户主动上传的素材。

支持类型：

| 类型 | 说明 |
| --- | --- |
| product_front | 商品正面图 |
| product_back | 商品背面图 |
| product_detail | 商品细节图 |
| model_reference | 模特参考图 |
| scene_reference | 场景参考图 |
| outfit_reference | 下装/搭配参考图 |
| reference_video | 参考视频 |
| user_keyframe | 用户上传关键帧 |
| generated_result | 用户上传的已生成结果 |

### 9.2 派生素材

派生素材指系统生成或处理后的素材。

| 类型 | 说明 |
| --- | --- |
| ai_keyframe | AI 生成关键帧图片 |
| image_variant | 图片变体 |
| video_clip | AI 生成视频片段 |
| final_video | Render Worker 合成成片 |
| cover_image | 视频封面 |

### 9.3 素材绑定要求

1. 用户可以手动绑定素材角色；
2. 系统可以推荐素材角色，但需要用户确认；
3. 一个任务可以有多张同类素材；
4. 一个镜头可以绑定一张或多张关键帧；
5. 后续模块必须读取绑定结果，不能自行猜测。

## 10. 关键帧配置

### 10.1 核心规则

视频生成需要关键帧，但关键帧不一定由 AI 生成。

每个镜头的关键帧来源可以是：

1. 用户上传图片；
2. 从已有素材库选择；
3. AI 生成图片；
4. AI 生成后用户替换；
5. 混合使用多张图片。

### 10.2 关键帧配置流程

```text
分镜确认完成
↓
系统检查每个镜头是否有关键帧
↓
如果用户上传了关键帧
  → 绑定到镜头
  → 跳过 AI 生图
↓
如果没有关键帧
  → 用户选择 AI 生图或继续上传
↓
确认关键帧
↓
进入视频片段生成
```

### 10.3 镜头关键帧字段

```json
{
  "shotId": "shot_001",
  "source": "user_upload | existing_asset | ai_generated",
  "assetId": "asset_123",
  "imagePurpose": "first_frame | last_frame | reference | product_detail",
  "userInstruction": "模特背对镜头，突出衣服背面印花",
  "confirmed": true
}
```

### 10.4 验收标准

1. 用户可以给每个镜头上传关键帧；
2. 用户上传关键帧后，可以跳过该镜头的 AI 生图；
3. 用户可以对部分镜头使用 AI 生图，对部分镜头使用上传图片；
4. 未确认关键帧的镜头不能进入视频生成；
5. 系统需要记录关键帧来源，方便后续修复和成本统计。

## 11. AI 生图需求

### 11.1 生图输入

AI 生图必须基于以下信息：

1. 商品信息；
2. 商品正反面约束；
3. 用户要求；
4. 镜头任务；
5. 镜头脚本；
6. 模特要求；
7. 场景要求；
8. 构图要求；
9. 光线要求；
10. 负面提示词；
11. 参考图片。

### 11.2 生图 Prompt 字段

```json
{
  "shotId": "shot_001",
  "imagePurpose": "keyframe",
  "userInstruction": "模特站在街头，突出衣服背面印花",
  "productConstraints": [
    "必须保留黑色宽松 T 恤",
    "必须保留背面大面积文字印花",
    "不能改变服装颜色"
  ],
  "scene": "urban street",
  "modelPose": "turning back",
  "camera": "medium full body shot",
  "style": "realistic TikTok fashion ad",
  "negativePrompt": [
    "wrong logo",
    "changed print",
    "extra limbs",
    "distorted clothes"
  ]
}
```

### 11.3 生图确认

用户可以对每张 AI 生成图片执行：

1. 确认使用；
2. 重生成；
3. 修改要求后重生成；
4. 替换为上传图片；
5. 删除该镜头图片约束；
6. 标记问题并进入修复循环。

## 12. AI 生视频需求

### 12.1 生视频输入

AI 生视频需要以下输入：

1. 已确认关键帧图片；
2. 镜头脚本；
3. 动作要求；
4. 运镜要求；
5. 视频时长；
6. 商品约束；
7. 模特约束；
8. 场景约束；
9. 负面提示词。

### 12.2 生视频粒度

V1.0 按镜头生成短视频片段，不直接让视频模型生成整条完整视频。

示例：

```text
20 秒视频
↓
4 个镜头
↓
每个镜头 5 秒
↓
分别生成 4 个 video_clip
↓
Render Worker 合成完整视频
```

### 12.3 生视频确认

用户可以对每个视频片段执行：

1. 确认使用；
2. 重生成该片段；
3. 修改动作后重生成；
4. 替换为用户上传视频；
5. 回退到关键帧重新生成；
6. 回退到分镜重新设计。

### 12.4 成本控制

1. 生视频前必须用户确认；
2. 单个镜头重生成只扣该镜头额度；
3. 系统默认不整条重生成；
4. 每个镜头设置最大自动重试次数；
5. 高成本模型调用需要明确提示用户；
6. 不同 provider 使用独立额度规则。

## 13. 脚本与分镜需求

### 13.1 分镜字段

```json
{
  "shotId": "shot_001",
  "order": 1,
  "duration": 5,
  "purpose": "hook with back print",
  "script": "Turn around and show the back print.",
  "visualDescription": "model stands side-back by the window",
  "action": "slow turn and look back",
  "camera": "mid-full body side-back shot",
  "scenePosition": "near window",
  "sellingPoint": "large back print",
  "requiresKeyframe": true,
  "keyframeStatus": "missing | generated | uploaded | confirmed",
  "videoClipStatus": "not_started | generating | generated | confirmed | rejected"
}
```

### 13.2 用户确认节点

脚本和分镜生成后，系统必须进入等待用户确认状态。

用户可以：

1. 修改镜头数量；
2. 修改每个镜头时长；
3. 修改文案；
4. 修改动作；
5. 修改景别；
6. 修改卖点；
7. 增加或删除镜头；
8. 要求系统重新生成某个镜头。

## 14. 参考视频解析与分镜迁移

### 14.1 解析内容

参考视频解析需要输出：

1. 总时长；
2. 镜头数量；
3. 每个镜头时间段；
4. 开头钩子；
5. 模特动作；
6. 机位；
7. 运镜；
8. 场景变化；
9. 光线风格；
10. 文案节奏；
11. 结尾 CTA；
12. 可复用结构。

### 14.2 输出示例

```json
{
  "duration": 18,
  "style": "real TikTok outfit video",
  "hook": "front-to-back contrast",
  "shots": [
    {
      "timeRange": "0-3s",
      "purpose": "front outfit hook",
      "camera": "mid-full body",
      "action": "adjust clothes",
      "motion": "static phone camera"
    },
    {
      "timeRange": "3-7s",
      "purpose": "back print reveal",
      "camera": "back view",
      "action": "turn around",
      "motion": "slow push in"
    }
  ]
}
```

### 14.3 迁移规则

1. 参考视频提供结构，不提供商品内容；
2. 当前商品卖点优先于参考视频结构；
3. 如果参考视频没有展示背面，但当前商品主卖点是背面印花，系统必须补充背面展示镜头；
4. 如果用户要求严格按照参考视频，则系统需要提示可能牺牲商品卖点展示；
5. 分镜迁移结果必须由用户确认。

## 15. 去重机制

### 15.1 视频结构指纹

每条视频方案都需要生成结构指纹。

```json
{
  "openingType": "back-facing by window",
  "mainAction": "side-back counter pose",
  "shotSequence": ["back hook", "front reveal", "side fit", "walking ending"],
  "cameraSequence": ["side-back", "front", "side", "tracking"],
  "scenePosition": ["left center", "center", "window counter", "walking path"],
  "endingType": "front walk ending",
  "mainVisual": "cafe lifestyle back print"
}
```

### 15.2 相似度规则

| 维度 | 权重 |
| --- | ---: |
| 开场方式相同 | 15 |
| 镜头顺序相同 | 20 |
| 主动作相同 | 20 |
| 机位组合相同 | 15 |
| 场景站位相同 | 10 |
| 结尾方式相同 | 15 |
| 主视觉相同 | 5 |

### 15.3 判定标准

| 分数 | 结果 |
| --- | --- |
| 0-45 | 差异足够，通过 |
| 46-70 | 存在重复风险，需要局部修改 |
| 71-100 | 高度重复，必须重组 |

## 16. 反馈修复循环

### 16.1 设计目标

AI 不应该在用户反馈后整条视频全部重来，而应该先判断问题类型，再回到对应模块局部修复。

### 16.2 问题类型

| 用户反馈 | 问题类型 | 修复位置 |
| --- | --- | --- |
| 衣服颜色不对 | product_constraint_error | 商品约束 / 生图 Prompt |
| 背面印花没展示 | selling_point_missing | 分镜 / 镜头任务 |
| 图片不像我要的场景 | scene_error | 场景约束 / 生图 Prompt |
| 模特动作僵硬 | action_error | 动作描述 / 生视频 Prompt |
| 视频片段跑偏 | video_clip_error | 视频片段 Prompt |
| 开头不吸引人 | script_or_hook_error | 脚本 / 分镜 |
| 和上一条太像 | duplication_error | 创意变体 / 去重 |
| 光线不对 | lighting_error | 视觉风格 / Prompt |

### 16.3 修复流程

```text
用户反馈
↓
问题分类
↓
定位影响范围
↓
决定回退节点
↓
局部修改
↓
QA 检查
↓
用户确认
↓
必要时重新生图/生视频/渲染
```

### 16.4 自动修复限制

1. 单次反馈最多自动修复 2 轮；
2. 超过 2 轮后必须交给用户确认；
3. 高成本视频重生成前必须用户确认；
4. 修复必须记录版本；
5. 修复不得丢失已有商品约束。

## 17. LangGraph 设计建议

### 17.1 是否需要 LangGraph

反馈修复、QA、问题分类和局部回退适合使用 LangGraph。

原因：

1. 流程存在分支；
2. 流程存在循环；
3. 需要根据问题类型决定下一节点；
4. 需要人工确认；
5. 需要最多重试次数；
6. 需要保留中间状态。

### 17.2 与现有架构关系

推荐分工：

```text
Java Backend
负责业务状态、用户确认、额度和版本

Temporal
负责长周期任务编排

LangGraph
负责 AI 阶段内部的复杂决策循环

Python AI Orchestrator
承载 LangGraph，并对外提供 AI 活动能力
```

### 17.3 适合 LangGraph 的子流程

1. 用户反馈修复；
2. Prompt QA 修复；
3. 视频片段问题诊断；
4. 分镜去重重组；
5. 用户自然语言需求解析；
6. 生图失败后的重试策略；
7. 生视频失败后的降级策略。

## 18. 任务状态机

V1.0 任务状态建议如下：

```text
CREATED
ASSET_UPLOADING
ASSET_ANALYZING
WAITING_ASSET_CONFIRMATION
REFERENCE_ANALYZING
PLAN_GENERATING
WAITING_PLAN_CONFIRMATION
STORYBOARD_GENERATING
WAITING_STORYBOARD_CONFIRMATION
KEYFRAME_CONFIGURING
IMAGE_GENERATING
WAITING_IMAGE_CONFIRMATION
VIDEO_CLIP_GENERATING
WAITING_VIDEO_CLIP_CONFIRMATION
RENDERING
WAITING_FINAL_REVIEW
REPAIRING
COMPLETED
FAILED
CANCELLED
```

### 18.1 状态原则

1. 等待用户确认的节点必须显式进入 WAITING 状态；
2. 前端根据状态展示下一步操作；
3. Python 不直接写业务状态，只通过回调通知 Java；
4. Java 校验状态流转是否合法；
5. 用户确认后才能进入高成本生成阶段。

## 19. 额度与成本控制

### 19.1 额度类型

建议拆分额度：

| 额度类型 | 用途 |
| --- | --- |
| text_llm_quota | 文本分析、脚本、分镜、QA |
| image_generation_quota | AI 生图 |
| video_generation_quota | AI 生视频 |
| render_quota | 最终视频合成 |

### 19.2 扣费原则

1. 文本 LLM 成本低，可以用于多轮草稿；
2. 生图成本中等，需要记录每张图；
3. 生视频成本高，必须用户确认；
4. 用户上传关键帧可以跳过 AI 生图成本；
5. 用户上传视频片段可以跳过 AI 生视频成本；
6. 局部重生成只扣局部额度；
7. 失败任务需要根据失败原因决定是否退回额度。

## 20. 数据结构建议

### 20.1 VideoTask 扩展字段

```json
{
  "taskId": "uuid",
  "taskMode": "PRODUCT_CREATIVE | REFERENCE_STORYBOARD | USER_SCRIPT | CUSTOM_STORYBOARD",
  "productCategory": "fashion",
  "status": "WAITING_STORYBOARD_CONFIRMATION",
  "duration": 20,
  "shotCount": 4,
  "targetPlatform": "TikTok",
  "currentVersion": 3
}
```

### 20.2 TaskAsset

```json
{
  "assetId": "uuid",
  "taskId": "uuid",
  "assetRole": "product_front",
  "assetKind": "image | video | audio",
  "source": "user_upload | ai_generated | external_url",
  "url": "string",
  "description": "string",
  "confirmed": true
}
```

### 20.3 CreativeState

```json
{
  "taskId": "uuid",
  "product": {},
  "model": {},
  "scene": {},
  "outfit": {},
  "referenceVideo": {},
  "constraints": {},
  "userRequirements": [],
  "updatedAt": "datetime"
}
```

### 20.4 Storyboard

```json
{
  "storyboardId": "uuid",
  "taskId": "uuid",
  "version": 1,
  "selected": true,
  "shots": []
}
```

### 20.5 Keyframe

```json
{
  "keyframeId": "uuid",
  "taskId": "uuid",
  "shotId": "shot_001",
  "source": "user_upload | ai_generated | existing_asset",
  "assetId": "uuid",
  "status": "draft | generating | generated | confirmed | rejected"
}
```

### 20.6 VideoClip

```json
{
  "clipId": "uuid",
  "taskId": "uuid",
  "shotId": "shot_001",
  "source": "ai_generated | user_upload",
  "url": "string",
  "duration": 5,
  "status": "generating | generated | confirmed | rejected"
}
```

### 20.7 RepairEvent

```json
{
  "repairId": "uuid",
  "taskId": "uuid",
  "targetType": "storyboard | keyframe | video_clip | final_video",
  "targetId": "string",
  "userFeedback": "背面印花没有展示清楚",
  "issueType": "selling_point_missing",
  "repairScope": "shot_002",
  "beforeVersion": 2,
  "afterVersion": 3,
  "createdAt": "datetime"
}
```

## 21. 页面需求

### 21.1 任务创建页

用户可以：

1. 创建视频任务；
2. 选择生成模式；
3. 设置视频时长；
4. 设置镜头数量；
5. 设置目标平台；
6. 输入整体要求；
7. 选择是否启用 AI 生图；
8. 选择是否启用 AI 生视频。

### 21.2 素材上传与绑定页

用户可以：

1. 上传商品图、模特图、场景图、参考视频；
2. 标注素材角色；
3. 查看系统推荐的角色；
4. 修改素材角色；
5. 删除或替换素材。

### 21.3 参考视频解析页

用户可以：

1. 查看参考视频拆解；
2. 查看镜头时长、动作、机位、节奏；
3. 修改解析错误；
4. 选择是否按参考分镜迁移。

### 21.4 创意方案页

用户可以：

1. 查看 3 套或更多创意方案；
2. 对比方案差异；
3. 查看相似度评分；
4. 选择一个方案；
5. 修改方案要求；
6. 要求重新生成方案。

### 21.5 分镜编辑页

用户可以：

1. 查看每个镜头卡片；
2. 编辑脚本；
3. 编辑动作；
4. 编辑时长；
5. 编辑机位；
6. 编辑卖点；
7. 增加、删除、重排镜头；
8. 确认分镜。

### 21.6 关键帧配置页

用户可以对每个镜头选择：

1. 上传图片；
2. 从已有素材选择；
3. AI 生成图片；
4. 修改生图要求；
5. 确认关键帧；
6. 替换关键帧。

### 21.7 视频片段确认页

用户可以：

1. 查看每个镜头生成的视频片段；
2. 单独重生成某个片段；
3. 修改该片段动作；
4. 替换为上传视频；
5. 确认片段进入合成。

### 21.8 成片预览与修复页

用户可以：

1. 播放最终视频；
2. 下载视频；
3. 反馈问题；
4. 指定问题发生在哪个镜头；
5. 选择局部修复；
6. 查看修复记录。

## 22. API 设计原则

本需求文档中的 API 需要融入现有项目架构。

原则：

1. 前端只调用 Java Backend；
2. Java Backend 调用 Python AI Orchestrator；
3. Python AI Orchestrator 不直接暴露给前端；
4. Render Worker 只消费渲染任务；
5. 所有任务状态以 Java Backend 为准；
6. 生图、生视频、渲染结果都通过 Java 记录。

建议接口方向：

```text
POST /api/video-tasks
POST /api/video-tasks/{taskId}/assets
PATCH /api/video-tasks/{taskId}/assets/{assetId}/role
POST /api/video-tasks/{taskId}/analyze-assets
POST /api/video-tasks/{taskId}/analyze-reference-video
POST /api/video-tasks/{taskId}/generate-plans
POST /api/video-tasks/{taskId}/confirm-plan
POST /api/video-tasks/{taskId}/generate-storyboard
POST /api/video-tasks/{taskId}/confirm-storyboard
POST /api/video-tasks/{taskId}/keyframes
POST /api/video-tasks/{taskId}/keyframes/{keyframeId}/confirm
POST /api/video-tasks/{taskId}/video-clips
POST /api/video-tasks/{taskId}/video-clips/{clipId}/confirm
POST /api/video-tasks/{taskId}/render
POST /api/video-tasks/{taskId}/feedback
POST /api/video-tasks/{taskId}/repair
```

## 23. 验收标准

V1.0 验收需要满足：

1. 用户可以创建服装视频任务；
2. 用户可以上传并绑定商品、模特、场景、参考视频和关键帧；
3. 用户可以选择商品创意生成、参考视频分镜迁移、用户脚本生成或自定义分镜；
4. 系统可以生成至少 3 套创意方案；
5. 用户可以选择或修改创意方案；
6. 系统可以生成脚本和分镜；
7. 用户可以确认或修改分镜；
8. 用户可以上传关键帧并跳过 AI 生图；
9. 用户可以选择 AI 生图并确认图片；
10. 系统可以基于关键帧和脚本生成视频片段；
11. 用户可以确认或重生成单个视频片段；
12. Render Worker 可以合成最终视频；
13. 用户可以对最终视频提出反馈；
14. 系统可以识别反馈问题类型；
15. 系统可以局部修复，而不是默认整条重来；
16. 高成本生视频操作前必须有用户确认；
17. 所有生成结果需要保留版本；
18. 所有生图、生视频、渲染消耗需要记录额度。

## 24. 开发阶段建议

### 阶段 1：状态与人工确认节点

目标：

1. 扩展任务状态机；
2. 增加素材角色绑定；
3. 增加分镜确认节点；
4. 增加关键帧配置节点。

### 阶段 2：脚本、分镜和参考视频迁移

目标：

1. 商品分析；
2. 参考视频解析；
3. 生成创意方案；
4. 生成脚本和分镜；
5. 用户可编辑分镜。

### 阶段 3：AI 生图与关键帧确认

目标：

1. 接入真实图片生成 provider；
2. 支持用户上传关键帧跳过生图；
3. 支持按镜头生成图片；
4. 支持图片确认和重生成。

### 阶段 4：AI 生视频与片段确认

目标：

1. 接入真实视频生成 provider；
2. 按镜头生成视频片段；
3. 支持单片段确认；
4. 支持单片段重生成；
5. 接入 Render Worker 合成成片。

### 阶段 5：LangGraph 修复循环

目标：

1. 用户反馈分类；
2. 局部修复决策；
3. QA 检查；
4. 最多 2 轮自动修复；
5. 用户确认后进入局部重生成。

## 25. 核心产品原则

1. 用户上传图片可以跳过 AI 生图；
2. 用户上传视频片段可以跳过 AI 生视频；
3. 生视频必须基于已确认关键帧；
4. 高成本生成前必须用户确认；
5. 反馈修复默认局部处理；
6. 商品约束不能在修复中丢失；
7. 分镜、图片、视频片段、成片都需要版本记录；
8. Java Backend 是唯一业务状态源；
9. Python AI Orchestrator 负责 AI 决策，不直接替代业务状态机；
10. Render Worker 只负责合成，不负责创意。

## 26. 一句话总结

本系统要做的不是一次性自动生成视频，而是构建一个可控、可确认、可回退、可循环修复的服装 AI 短视频生产工作台：用户提供素材和要求，AI 生成脚本、分镜、图片和视频片段，用户在关键节点参与确认，系统根据反馈进行局部修复，并最终合成可用的短视频成片。
