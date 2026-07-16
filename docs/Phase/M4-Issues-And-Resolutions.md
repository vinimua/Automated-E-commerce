# M4 问题与解决方案整理

阶段：M4 关键帧页与 fake 生图切片  
日期：2026-07-16  
范围：`/keyframes` 页面、Java keyframe 主链路、Python fake keyframe provider、M4 验收文档  
结论：本轮已修复 M4 关键数据完整性问题，仍需跑真实任务做端到端验收。

---

## 1. 背景

M4 的目标不是接入真实图片生成模型，而是在真实 Provider 之前，把关键帧阶段的业务闭环跑通：

```text
storyboard confirmed
  -> keyframe_configuring
  -> 用户上传关键帧 / 请求 AI fake 生成
  -> image_generating
  -> fake callback 写入 keyframes
  -> waiting_image_confirmation
  -> 用户逐帧确认 / 驳回 / 重新生成
  -> 所有 storyboard shots 都确认后进入 video_clip_generating
```

复盘后发现，M4 原来的问题不在“有没有接口或页面”，而在验收判断过松：只要页面和 API 存在，就被写成通过，但关键帧数据完整性、状态推进条件、fake 图片预览、shot 关联关系都存在缺口。

---

## 2. 已解决的问题

### M4-001：只确认部分关键帧也可能推进到视频片段生成

严重级别：高  
影响范围：状态机、视频片段生成、M5/M6 后续链路  
涉及文件：

- `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/service/impl/KeyframeServiceImpl.java`

#### 问题现象

原逻辑只统计当前版本 keyframe 表中未确认记录的数量：

```java
count status != 'confirmed'
```

如果 storyboard 有 6 个 shots，但数据库里只存在 1 条 keyframe，且这一条已经 confirmed，那么未确认数量就是 0，系统可能误以为“全部确认完成”，从而进入：

```text
video_clip_generating
```

这会导致视频片段生成阶段缺关键帧输入。

#### 根因

系统把“已存在 keyframe 记录的确认状态”当成了“storyboard 所有 shot 的确认状态”。

正确基准应该是 storyboard shots，而不是 keyframes 表里已有多少条记录。

#### 解决方案

新增完整性判断：

```text
对当前 storyboard 的每个 shot：
  1. 必须存在当前版本 keyframe
  2. keyframe.status 必须等于 confirmed
  3. 如果 keyframe.shotId 已存在，则必须和 storyboard shot.id 对得上
```

只有全部满足，才允许进入：

```text
waiting_image_confirmation -> video_clip_generating
```

#### 修复后行为

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 6 个 shots，只确认 1 个 | 可能推进到 video_clip_generating | 不推进 |
| 6 个 shots，存在 5 个 confirmed，缺 1 个 | 可能推进 | 不推进 |
| 6 个 shots，全部 confirmed | 推进 | 推进 |

---

### M4-002：批量 generate 没有处理 rejected / failed keyframe

严重级别：高  
影响范围：用户重新生成关键帧、M4 验收规则  
涉及文件：

- `KeyframeServiceImpl.java`

#### 问题现象

M4 规则要求：

```text
批量 generate 只能生成 missing / rejected / failed 的 shots
```

但原逻辑是：

```text
只要某个 shot 已经存在 keyframe，就跳过
```

所以 rejected / failed 的关键帧不会被批量重新生成，用户只能逐帧 regenerate。

#### 根因

原逻辑只判断“是否存在 keyframe”，没有根据 keyframe status 判断是否应该重新生成。

#### 解决方案

批量生成时按状态分类：

应该生成：

- missing
- draft
- rejected
- failed

应该跳过：

- uploaded
- generated
- confirmed
- generating

#### 修复后行为

| keyframe 状态 | 批量 generate 行为 |
|---|---|
| missing | 生成 |
| draft | 生成 |
| rejected | 重新生成 |
| failed | 重新生成 |
| uploaded | 跳过 |
| generated | 跳过 |
| confirmed | 跳过 |
| generating | 跳过 |

---

### M4-003：同一 shot 可能产生重复 keyframe

严重级别：中高  
影响范围：前端展示、确认逻辑、后续视频片段生成  
涉及文件：

- `KeyframeServiceImpl.java`

#### 问题现象

同一个 shot 多次上传或请求 AI 生成时，可能插入多条 keyframe。

前端通过：

```ts
keyframes.find((k) => k.shotNo === shotNo)
```

拿第一条匹配记录。如果旧记录是 rejected / draft，新记录已经 uploaded / generated，前端仍可能显示旧状态，造成用户误解。

#### 根因

创建 keyframe 时没有使用：

```text
taskId + shotNo + currentVersion
```

作为当前版本下同一 shot 的幂等键。

#### 解决方案

创建/上传/请求 AI 生成时：

1. 先查询当前版本是否已有同 shotNo 的 keyframe。
2. 如果已有，更新这条记录。
3. 如果没有，才插入新记录。
4. 如果已有记录是 `confirmed`，不允许直接覆盖。

#### 修复后行为

| 场景 | 行为 |
|---|---|
| shot 1 第一次上传 | 插入 keyframe |
| shot 1 rejected 后重新上传 | 更新原 keyframe |
| shot 1 failed 后批量生成 | 更新原 keyframe |
| shot 1 confirmed 后再次上传 | 拒绝覆盖 |

---

### M4-004：keyframe 缺少 storyboard shot 关联

严重级别：中高  
影响范围：M5 视频片段、repair、render manifest  
涉及文件：

- `KeyframeServiceImpl.java`
- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java`

#### 问题现象

`KeyframeEntity` 有字段：

```java
storyboardId
shotId
```

但原先创建 keyframe 时没有稳定填充。后续只能靠 `shotNo` 关联 storyboard shot。

#### 根因

M4 只关注了“每个 shotNo 有一张关键帧”，没有把 keyframe 和 storyboard shot 的真实主键关系建立起来。

#### 风险

只靠 `shotNo` 有几个隐患：

- 分镜重排后 shotNo 可能变化。
- 多版本 repair 后 shotNo 可能复用。
- render manifest 难以稳定追踪素材来源。
- M5/M6 生成视频片段时不好定位对应分镜。

#### 解决方案

在以下路径补齐 `storyboardId` 和 `shotId`：

1. 用户上传 keyframe。
2. 用户单帧请求 AI 生成。
3. 批量生成 keyframe。
4. Python keyframe callback 回写结果。

#### 修复后数据关系

```text
storyboards.id
  -> storyboard_shots.storyboard_id
  -> keyframes.storyboard_id
  -> keyframes.shot_id
```

后续视频片段和 render manifest 可以优先使用 `shotId` 做稳定关联。

---

### M4-005：fake provider 返回不可预览 URL

严重级别：中  
影响范围：前端验收、用户体验  
涉及文件：

- `services/ai-orchestrator/src/providers/fake_image.py`

#### 问题现象

原 fake provider 返回类似：

```text
https://placeholder.cos.ap-guangzhou.myqcloud.com/...
```

如果这个域名或路径不可访问，前端会显示 broken image。

这和 M4 验收标准冲突：

```text
callback 后能看到生成图片预览
```

#### 根因

fake provider 返回的是外部占位 URL，但没有保证资源真实存在。

#### 解决方案

fake provider 改为返回内联 SVG data URL：

```text
data:image/svg+xml;base64,...
```

特点：

- 不依赖外部网络。
- 不消耗模型费用。
- 每个 shot 返回确定性预览图。
- 前端 `<img>` 可以直接显示。

#### 修复后行为

| 场景 | 修复前 | 修复后 |
|---|---|---|
| fake callback 返回图片 | 可能 broken image | 直接显示 SVG 占位图 |
| 离线开发 | 可能无法预览 | 可以预览 |
| CI / 本地验收 | 不稳定 | 稳定 |

---

### M4-006：M4 验收文档结论过早且编码损坏

严重级别：中  
影响范围：复盘、交接、后续阶段判断  
涉及文件：

- `docs/Phase/M4-Results-And-Acceptance.md`
- `docs/Phase/M4-Issues-And-Resolutions.md`

#### 问题现象

原文档存在两个问题：

1. 大量中文乱码。
2. 在关键链路没有端到端验证前，直接写成“已通过”。

#### 根因

文档把“代码存在 / 编译通过 / 页面存在”当成了“业务流程通过”。

#### 解决方案

重写 M4 验收文档和问题解决文档：

- 使用 UTF-8 中文。
- 明确区分：
  - 已修复的问题
  - 已验证的内容
  - 仍需端到端验收的内容
- 将 M4 状态改为：

```text
代码层关键缺口已修复；
还需要跑一条真实任务做端到端验收后，才能把 M4 标记为完全通过。
```

---

## 3. 修复后的关键流程

### 3.1 批量生成关键帧

```text
用户点击一键 AI 生成
  -> Java 读取 storyboard shots
  -> Java 找出 missing / draft / rejected / failed 的 shots
  -> Java 为目标 shots 创建或更新 keyframe 为 generating
  -> Java 调用 Python /ai/workflows/keyframe-generation
  -> Python fake_generate_keyframes 返回 data URL
  -> Python callback Java stage=keyframe
  -> Java 写入 keyframes
  -> Java 推进到 waiting_image_confirmation
```

### 3.2 用户逐帧确认

```text
用户确认一个 keyframe
  -> Java 设置该 keyframe.status = confirmed
  -> Java 检查当前 storyboard 每个 shot 是否都有 confirmed keyframe
  -> 如果没有全部确认：停留当前状态
  -> 如果全部确认：进入 video_clip_generating
```

### 3.3 单帧重新生成

```text
用户驳回 / 生成失败
  -> keyframe.status = rejected / failed
  -> 用户点击重新生成
  -> Java 将该 keyframe 设置为 generating
  -> Java 只发送该 shot 的 storyboard payload
  -> Python fake 生成单帧
  -> callback 只更新该 shot 的 keyframe
```

---

## 4. 修改文件清单

| 文件 | 修改内容 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/keyframe/service/impl/KeyframeServiceImpl.java` | 修复关键帧完整性判断；批量生成支持 rejected/failed；避免同 shot 重复 keyframe；写入 storyboardId/shotId |
| `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java` | keyframe callback 回写时补齐 storyboardId/shotId |
| `services/ai-orchestrator/src/providers/fake_image.py` | fake provider 改为返回 SVG data URL，保证前端可预览 |
| `docs/Phase/M4-Results-And-Acceptance.md` | 重写 M4 验收结论，修复乱码，标注仍需 E2E 验收 |
| `docs/Phase/M4-Issues-And-Resolutions.md` | 本文档，整理问题、根因和方案 |

---

## 5. 验证结果

已执行：

```text
python -m py_compile services/ai-orchestrator/src/providers/fake_image.py services/ai-orchestrator/src/activities/fake_generate_keyframes.py
```

结果：通过。

```text
./gradlew.bat compileJava
```

结果：通过。

```text
npm run build
```

结果：通过。

说明：

Java 编译第一次在沙箱内遇到 Gradle wrapper 锁权限问题，使用授权后的非沙箱命令验证通过。

---

## 6. 仍需端到端验收的场景

建议用一个包含 3 个 storyboard shots 的任务验证：

1. 进入 `/keyframes`，应看到 3 张 shot 卡片。
2. 只上传并确认 shot 1，任务不应进入 `video_clip_generating`。
3. 批量 AI 生成其余 missing shots。
4. fake callback 后应看到可预览图片，而不是 broken image。
5. reject 一个 keyframe 后，批量 generate 应重新处理该 shot。
6. failed keyframe 也应可被批量 generate 重新处理。
7. 三个 keyframes 全部 confirmed 后，任务才进入 `video_clip_generating`。
8. 进入 M5 `/clips` 时，应能拿到完整 keyframes payload。

---

## 7. 当前结论

M4 的代码层关键缺口已经修复。

但 M4 不能只靠编译和页面存在来判定完成。最终通过条件应该是：

```text
真实任务端到端跑通：
storyboard -> keyframes -> confirm all -> video_clip_generating
```

在完成上述 E2E 验收前，M4 状态应标记为：

```text
关键问题已修复，待端到端验收。
```
