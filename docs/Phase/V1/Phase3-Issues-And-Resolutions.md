# Phase 3 问题整改说明

> 项目：TikTok Shop AI 带货视频生成系统  
> 阶段：Phase 3 前端工作台  
> 整改日期：2026-06-15  
> 目的：记录阶段三验收中发现的问题、影响范围、解决方式和验证结果，作为第三方验收和后续 AI 接手的输入材料。

---

## 1. 整改结论

阶段三原始结果具备基本前端页面和构建能力，但存在验收证据不足、契约漂移、认证状态不一致、任务状态 UI 不准确、lint 不可复现等问题。

本次整改后：

- OpenAPI 类型已改为自动生成。
- 前端、OpenAPI、Java 创建任务 DTO 的 V1 `videoType` 已对齐。
- `npm run lint` 已可非交互执行并通过。
- JWT refresh 失败后的前端登录态已同步清理。
- 任务页完成态、失败态、方案空态已修正。
- 阶段三结果文档已更新为真实验收证据。

当前结论：**阶段三可作为有条件通过提交验收**。剩余主要风险是缺少前端 E2E 自动化测试，按路线图归 Phase 6。

---

## 2. 问题与整改明细

### 2.1 问题一：前端 API 类型与 OpenAPI 存在漂移风险

**问题描述**

原 `apps/web/src/types/api.generated.ts` 文件名显示为 generated，但实际是手写维护。手写类型容易与 `docs/02-openapi-spec.yaml` 发生漂移。

其中最明显的问题是：

- OpenAPI 中 `VideoType` 是 V1 的 3 种类型。
- 旧前端类型仍保留 6 种类型。

**影响**

- 前端可能允许或引用 OpenAPI 当前不支持的枚举值。
- 后续接口字段变化时，TypeScript 无法可靠暴露契约变化。
- 阶段验收中“类型与 OpenAPI 一致”的结论缺乏可信证据。

**解决方案**

1. 使用 `openapi-typescript` 从 OpenAPI 重新生成 `api.generated.ts`。
2. 新增 `generate:api-types` npm script，避免以后手动维护生成文件。
3. 在 `api.ts` 中保留应用层解包后的稳定类型别名，避免页面直接依赖生成文件中的复杂响应 envelope。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/src/types/api.generated.ts` | 改为由 OpenAPI 自动生成 |
| `apps/web/src/types/api.ts` | 增加应用层类型别名 |
| `apps/web/package.json` | 新增 `generate:api-types` 脚本 |

**验证方式**

```bash
cd apps/web
npm run generate:api-types
npm run type-check
```

验证结果：通过。

---

### 2.2 问题二：`VideoType` 契约没有在前后端入口统一收紧

**问题描述**

阶段三 V1 只允许创建以下 3 种视频类型：

- `pain_point_solution`
- `before_after`
- `review`

旧实现中：

- 前端 UI 只展示 3 种。
- Java service 层会拒绝非 V1 类型。
- 但 Java DTO 注解仍允许 6 种类型。

**影响**

API 入参校验层与业务层行为不一致。第三方验收时会认为 OpenAPI、DTO、service 之间存在契约不一致。

**解决方案**

将 Java `CreateVideoTaskRequest.videoType` 的 `@Pattern` 从 6 种收紧为 V1 的 3 种。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/api-java/src/main/java/com/tk/ai/video/module/videotask/dto/CreateVideoTaskRequest.java` | 收紧 `videoType` 校验 |
| `apps/web/src/types/api.generated.ts` | OpenAPI 生成类型中 `VideoType` 为 3 种 |

**验证方式**

```bash
cd apps/api-java
.\gradlew.bat test
```

验证结果：通过。

---

### 2.3 问题三：lint 不能作为验收证据

**问题描述**

阶段三文档原先声称“0 lint 错误”，但实际执行 `npm run lint` 时会进入 Next.js ESLint 初始化交互，不能作为可复现验收命令。

**影响**

- 第三方无法复现“lint 通过”。
- 文档中的验收结论不可信。
- CI 或自动化验收无法直接运行 lint。

**解决方案**

新增 Next.js ESLint 配置文件，并关闭当前项目中合理存在的 `<img>` 规则限制。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/.eslintrc.json` | 新增 ESLint 配置 |
| `apps/web/src/app/dashboard/page.tsx` | 将 lucide `Image` 图标重命名为 `ImageIcon`，避免 a11y 误判 |
| `apps/web/src/app/video-tasks/[id]/plans/page.tsx` | 修复 `useEffect` 依赖警告 |

**验证方式**

```bash
cd apps/web
npm run lint
```

验证结果：

```text
✔ No ESLint warnings or errors
```

---

### 2.4 问题四：refresh token 失败后前端登录状态不同步

**问题描述**

旧逻辑中，`api-client` 在 refresh 失败后只清理 localStorage 中的 token，但 `AuthProvider` 的 `isLoggedIn` 状态不会同步变化。

**影响**

用户可能处于一种错误状态：

- 页面认为用户已登录。
- 实际 token 已被清除。
- 后续 API 请求继续失败。

**解决方案**

1. 在 `api-client` 中新增认证过期事件 `tk-auth-expired`。
2. refresh 失败时清理 token 并派发事件。
3. `AuthProvider` 监听该事件，统一清理 React 登录态并跳转 `/login`。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/src/lib/api-client.ts` | refresh 失败后派发认证过期事件 |
| `apps/web/src/hooks/use-auth.tsx` | 监听认证过期事件并同步登出 |

**验证方式**

```bash
cd apps/web
npm run type-check
npm run build
```

验证结果：通过。

---

### 2.5 问题五：JWT 本地恢复时没有检查过期时间

**问题描述**

旧逻辑只要 localStorage 存在 access token，就解码 payload 并设置为已登录，没有检查 `exp`。

**影响**

过期 token 也可能让前端短暂进入已登录状态，直到 API 请求失败。

**解决方案**

在 `use-auth.tsx` 中新增：

- `decodeJwtPayload`
- `isExpired`

初始化登录态时，如果 token 已过期，立即清理 token，不进入已登录状态。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/src/hooks/use-auth.tsx` | 增加 JWT `exp` 检查 |

**验证方式**

```bash
cd apps/web
npm run type-check
npm run build
```

验证结果：通过。

---

### 2.6 问题六：任务完成态 UI 错误

**问题描述**

旧任务页中，`completed` 被归入 `isPostSelection`，导致任务完成后仍显示：

```text
AI 正在生成脚本、分镜和素材...
```

**影响**

用户会误以为任务仍在进行。阶段验收时，状态机 UI 与业务状态不一致。

**解决方案**

1. 将 `completed` 和 `exported` 从进行中状态中移除。
2. 新增 `isCompleted` 分支。
3. 完成态显示明确的完成说明。
4. `TaskProgress` 对 `completed` 和 `exported` 按完成处理。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/src/app/video-tasks/[id]/plans/page.tsx` | 新增完成态 UI |
| `apps/web/src/components/task-progress.tsx` | 完成态进度条全部完成 |

**验证方式**

```bash
cd apps/web
npm run build
```

验证结果：通过。

---

### 2.7 问题七：方案选择页缺少空态和重试错误处理

**问题描述**

旧任务页中：

- `waiting_plan_selection` 但 plans 为空时没有明确提示。
- 点击重试失败时没有捕获错误。

**影响**

- 用户不知道当前是等待、异常还是没有数据。
- 重试失败后缺少反馈。

**解决方案**

1. 增加 `waiting_plan_selection && plans.length === 0` 空态。
2. 给 retry 操作增加 `try/catch`。
3. 重试失败时显示错误信息。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/src/app/video-tasks/[id]/plans/page.tsx` | 新增方案空态和重试错误处理 |

**验证方式**

```bash
cd apps/web
npm run lint
npm run build
```

验证结果：通过。

---

### 2.8 问题八：产品分析页分析区显示条件过窄

**问题描述**

旧逻辑只有 `product.category` 存在时才显示整个“商品分析”区域。

如果后端只返回了卖点、痛点、受众、场景、评分或风险提示，但没有 category，页面不会显示这些已有分析结果。

**影响**

有效数据可能被隐藏，用户误以为 AI 没有分析结果。

**解决方案**

新增 `hasAnalysis` 判断，只要以下任一字段存在就展示分析区：

- `category`
- `sellingPoints`
- `painPoints`
- `targetAudience`
- `scenes`
- `videoScore`
- `riskTips`

如果没有任何分析字段，展示“分析结果尚未生成”的空态。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `apps/web/src/app/products/[id]/analysis/page.tsx` | 修正分析区显示条件并增加空态 |

**验证方式**

```bash
cd apps/web
npm run type-check
npm run build
```

验证结果：通过。

---

### 2.9 问题九：阶段三结果文档与真实状态不一致

**问题描述**

阶段三文档中存在以下过期或不准确内容：

- 声称类型完全匹配 OpenAPI，但旧类型是手写。
- 声称 lint 没有错误，但当时 lint 不能非交互运行。
- `VideoType` 描述仍按 6 种写法，没有体现 V1 OpenAPI 收紧。
- 构建、lint、测试证据不是最新执行结果。

**影响**

第三方验收会被误导。AI 后续接手时也容易基于错误文档做错误判断。

**解决方案**

更新阶段三文档：

- 文档版本更新为 v1.1。
- 增加 `generate:api-types` 验证记录。
- 更新 lint、type-check、build、后端 test 的真实结果。
- 更新契约对齐说明。
- 更新风险清单。

**涉及文件**

| 文件 | 说明 |
|---|---|
| `docs/Phase3-Implementation-Documentation.md` | 更新阶段三验收文档 |

---

## 3. 最终验证结果

以下命令均已执行并通过。

### 3.1 前端 OpenAPI 类型生成

```bash
cd apps/web
npm run generate:api-types
```

结果：通过。

### 3.2 前端构建

```bash
cd apps/web
npm run build
```

结果：通过。

关键输出：

```text
✓ Compiled successfully
✓ Linting and checking validity of types
✓ Generating static pages (8/8)
```

### 3.3 前端类型检查

```bash
cd apps/web
npm run type-check
```

结果：通过。

### 3.4 前端 lint

```bash
cd apps/web
npm run lint
```

结果：

```text
✔ No ESLint warnings or errors
```

### 3.5 后端测试

```bash
cd apps/api-java
.\gradlew.bat test
```

结果：

```text
BUILD SUCCESSFUL
```

---

## 4. 当前剩余风险

| 编号 | 风险 | 严重级别 | 是否阻塞阶段三验收 | 说明 |
|---|---|---|---|---|
| RISK-001 | 前端缺少 E2E 自动化测试 | 中 | 否 | Phase 6 计划补集成测试 |
| RISK-002 | Token 存储在 localStorage | 中 | 否 | V1 可接受，V2 可考虑 httpOnly cookie |
| RISK-003 | 任务终态后仍会继续轮询 | 低 | 否 | UI 已正确显示终态，后续可优化停止轮询 |
| RISK-004 | 分镜编辑相关后端旧镜头清理问题 | 中 | 否 | 属于后续分镜编辑阶段，不阻塞 Phase 3 |

---

## 5. 后续建议

1. Phase 4 / Phase 5 继续推进前，保持 OpenAPI 为唯一 API 契约来源。
2. 每次修改 `docs/02-openapi-spec.yaml` 后执行：

```bash
cd apps/web
npm run generate:api-types
npm run type-check
```

3. Phase 6 补前端 E2E，至少覆盖：

- 注册
- 登录
- 创建商品
- 创建视频任务
- 方案选择
- 失败重试

4. 后续阶段文档统一使用：

```text
docs/Phase-Result-Acceptance-Template.md
```

不要只写实现说明，必须写清楚验收证据。

