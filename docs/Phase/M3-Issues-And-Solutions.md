# M3 问题发现与实际修复记录

> 阶段：M3 方案与分镜切片  
> 检验对象：`docs/Phase/M3-Results-And-Acceptance.md` 与当前代码实现  
> 修复日期：2026-07-11  
> 当前结论：本次发现的 M3 阻塞问题已修复，并已通过 Java 测试与前端构建验证。

---

## 1. 本次检验结论

M3 原验收文档中声明主链路已经通过，但实际代码存在几类问题：

- `storyboard` callback 幂等判断不完整，重复回调可能重复写入 storyboard/shots。
- `storyboard` callback 在状态合法性确认之前就写入数据库。
- `confirmStoryboard()` 可能出现“状态切到关键帧阶段，但实际没有触发关键帧生成”的假成功。
- `/plans` 页面没有覆盖 `plan_generating` 加载态。
- Progress 页面过早显示 storyboard 入口。
- M3 核心后端逻辑缺少测试覆盖。

这些问题已经按代码修复，并补充了后端单元测试。

---

## 2. 问题与修复状态

| 编号 | 问题 | 严重级别 | 修复状态 | 实际修复 |
|---|---|---:|---|---|
| M3-001 | `storyboard` callback 幂等判断不完整 | 高 | 已修复 | 扩展 `storyboard` 已处理状态集合 |
| M3-002 | `storyboard` callback 先写库、后推进状态 | 高 | 已修复 | 写库前先要求任务必须处于 `storyboard_generating` |
| M3-003 | `confirmStoryboard()` 缺少 storyboard/rawAiOutput 仍会切状态 | 高 | 已修复 | 前置校验 storyboard 和 rawAiOutput，失败时不切状态、不调用 Python |
| M3-004 | `/plans` 缺少 `plan_generating` 加载态 | 中 | 已修复 | 将 `plan_generating` 加入方案生成中状态 |
| M3-005 | Progress 页过早展示 storyboard 入口 | 中 | 已修复 | 只有 storyboard 已生成后的状态才展示入口 |
| M3-006 | M3 核心逻辑缺少测试 | 中 | 已修复 | 新增 callback 测试，补充 `confirmStoryboard()` 测试 |

---

## 3. 实际修复详情

### M3-001：补全 storyboard callback 幂等判断

修复文件：

- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java`

修复前：

`isStageAlreadyProcessed("storyboard", taskStatus)` 只把旧链路的状态视为已处理，例如：

```text
script_generated
material_generating
material_generated
rendering
checking
completed
exported
```

这会漏掉 Fashion Creative Loop 的后续状态，例如：

```text
waiting_storyboard_confirmation
keyframe_configuring
image_generating
waiting_image_confirmation
```

修复后：

`storyboard` callback 在以下状态会被视为已经处理过，不会重复插入 storyboard/shots：

```text
waiting_storyboard_confirmation
keyframe_configuring
image_generating
waiting_image_confirmation
video_clip_generating
waiting_video_clip_confirmation
rendering
waiting_final_review
script_generated
material_generating
material_generated
checking
completed
exported
```

实际效果：

- 重复 `stage=storyboard` callback 不再重复写入 storyboard。
- 任务进入关键帧或后续阶段后，旧 storyboard callback 会被安全忽略。

---

### M3-002：storyboard callback 写库前增加状态保护

修复文件：

- `apps/api-java/src/main/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImpl.java`

修复前：

`case "storyboard"` 会先插入 `StoryboardEntity` 和 `StoryboardShotEntity`，最后才调用：

```java
advanceTask(task, "waiting_storyboard_confirmation");
```

如果当前任务状态已经被推进到后续阶段，前面的写库动作仍可能已经发生。

修复后：

写库前新增状态保护：

```java
if (!"storyboard_generating".equals(task.getStatus())) {
    log.info("Storyboard callback skipped because task is not generating storyboard: taskId={}, status={}",
            task.getId(), task.getStatus());
    return;
}
```

实际效果：

- 只有任务处于 `storyboard_generating` 时，callback 才允许写 storyboard/shots。
- 已进入 `waiting_storyboard_confirmation` 或更后状态时，不会产生重复数据。

---

### M3-003：confirmStoryboard() 避免假成功

修复文件：

- `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImpl.java`

修复前：

`confirmStoryboard()` 会先把任务切到：

```text
keyframe_configuring
```

然后才尝试查 storyboard 和 `rawAiOutput`。  
如果 storyboard 不存在，或 `rawAiOutput` 为空，接口仍可能返回成功，但不会触发 Python 关键帧生成。

修复后：

把 storyboard 校验提前：

```java
StoryboardEntity storyboard = storyboardMapper.findByTaskId(taskId)
        .orElseThrow(() -> new BusinessException("Storyboard not found for task"));
Map<String, Object> storyboardMap = storyboard.getRawAiOutput();
if (storyboardMap == null || storyboardMap.isEmpty()) {
    throw new BusinessException("Storyboard raw AI output is empty");
}
```

只有校验通过后，才执行：

```java
task.setStatus("keyframe_configuring");
videoTaskMapper.updateById(task);
aiServiceClient.startKeyframeGeneration(taskId, task.getProductId(), userId, storyboardMap);
```

实际效果：

- storyboard 不存在时，不切状态。
- `rawAiOutput` 为空时，不切状态。
- 成功确认分镜时，一定会调用 `startKeyframeGeneration()`。

---

### M3-004：/plans 页面补充 plan_generating 加载态

修复文件：

- `apps/web/src/app/video-tasks/[id]/plans/page.tsx`

修复前：

`/plans` 页面的生成中状态只包含：

```ts
["analyzing", "analysis_completed", "plan_generated"]
```

没有包含 Fashion Creative Loop 使用的：

```text
plan_generating
```

修复后：

```ts
const isInProgress = ["plan_generating", "analyzing", "analysis_completed", "plan_generated"].includes(task.status);
```

实际效果：

- 用户在 `plan_generating` 阶段进入 `/plans`，会看到方案生成中的加载态。
- 不会出现主体空白或无明确反馈。

---

### M3-005：Progress 页修正 storyboard 入口展示条件

修复文件：

- `apps/web/src/app/video-tasks/[id]/progress/page.tsx`

修复前：

只要存在 `selectedPlanId`，Progress 页就展示 storyboard 入口：

```tsx
{task.selectedPlanId && !isTerminal && (...)}
```

这会导致 `storyboard_generating` 阶段过早进入 `/storyboard`，看到“分镜不存在”。

修复后：

新增 `canViewStoryboard`，只在 storyboard 已经生成或进入后续阶段时展示入口：

```ts
const canViewStoryboard = [
  "waiting_storyboard_confirmation",
  "keyframe_configuring",
  "image_generating",
  "waiting_image_confirmation",
  "video_clip_generating",
  "waiting_video_clip_confirmation",
  "rendering",
  "waiting_final_review",
  "completed",
  "exported",
].includes(task.status);
```

实际效果：

- `storyboard_generating` 阶段不会展示“查看分镜脚本”入口。
- `waiting_storyboard_confirmation` 及后续阶段才允许查看分镜。

---

### M3-006：补充 M3 后端测试

新增/修改文件：

- `apps/api-java/src/test/java/com/tk/ai/video/module/callback/service/impl/AiCallbackServiceImplTest.java`
- `apps/api-java/src/test/java/com/tk/ai/video/module/videotask/service/impl/VideoTaskServiceImplTest.java`

新增测试覆盖：

1. `storyboardCallbackPersistsStoryboardShotsAndAdvancesTask`
   - 首次 storyboard callback 会插入 storyboard。
   - 会插入 storyboard shot。
   - 会推进任务到 `waiting_storyboard_confirmation`。

2. `storyboardCallbackIsIdempotentAfterStoryboardAlreadyProcessed`
   - 当前状态已经是 `waiting_storyboard_confirmation` 时，重复 callback 不插入 storyboard。
   - 不插入 shots。
   - 不更新任务状态。

3. `confirmStoryboardValidatesStoryboardAndStartsKeyframeGeneration`
   - storyboard 存在且 `rawAiOutput` 有效时，状态切到 `keyframe_configuring`。
   - 调用 `aiServiceClient.startKeyframeGeneration()`。

4. `confirmStoryboardRejectsMissingStoryboardBeforeStateChange`
   - storyboard 不存在时抛出 `BusinessException`。
   - 不切换任务状态。
   - 不调用 Python。

---

## 4. 验证结果

已执行：

```powershell
cd "D:\Web\Automated E-commerce\apps\api-java"
.\gradlew.bat test
```

结果：

```text
BUILD SUCCESSFUL
```

已执行：

```powershell
cd "D:\Web\Automated E-commerce\apps\web"
npm run build
```

结果：

```text
Compiled successfully
Linting and checking validity of types passed
Static pages generated successfully
```

---

## 5. 修复后的 M3 真实链路

当前代码中的 M3 链路应为：

```text
waiting_plan_selection
  -> 用户确认方案
storyboard_generating
  -> Python storyboard callback
waiting_storyboard_confirmation
  -> 用户确认分镜
keyframe_configuring
  -> Java 自动触发 Python keyframe generation
image_generating / waiting_image_confirmation
```

关键保护：

- 重复 storyboard callback 会被幂等拦截。
- storyboard callback 只有在 `storyboard_generating` 状态下才写库。
- 确认分镜前必须存在有效 storyboard/rawAiOutput。
- 确认分镜成功后一定触发关键帧生成。
- 前端不会在分镜未生成时展示 storyboard 入口。

---

## 6. 剩余风险

本次修复解决了 M3 发现的阻塞问题，但仍有以下后续建议：

1. 前端还没有 E2E 测试覆盖完整 M3 用户路径。
2. callback 幂等仍主要依赖任务状态，没有引入 callback event id 或阶段级幂等表。
3. 当前测试覆盖了核心成功/重复/缺失 storyboard 场景，但还可以继续补 `rawAiOutput` 为空的单独测试。
4. 需要用真实 Python callback 再做一次端到端联调，确认 payload 字段与 Java 入库字段完全匹配。

---

## 7. 当前结论

M3 原先发现的问题已经按实际代码修复。  
以当前代码状态看，M3 的后端状态推进、storyboard callback 幂等、分镜确认触发关键帧、以及前端关键入口展示逻辑已经达到阶段验收要求。
