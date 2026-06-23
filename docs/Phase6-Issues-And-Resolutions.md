# Phase 6 问题与解决方案汇总

---

## 0. 文档元信息

| 项目 | 内容 |
|---|---|
| 项目名称 | TikTok Shop AI 带货视频生成系统 |
| 阶段编号 | Phase 6 |
| 阶段名称 | 前端收尾 + 管理后台 + 联调 |
| 文档用途 | 汇总阶段 6 验收中发现的问题、直接影响、修复方案、验证结果和剩余风险 |
| 生成日期 | 2026-06-23 |
| 关联文档 | `docs/Phase6-Results-And-Acceptance.md`、`docs/V1-Development-Roadmap.md` |
| 当前结论 | 已修复用户侧成片跳转、补齐用户/视频管理页；额度管理和真实 E2E 验证仍是 V1 阻塞项 |

---

## 1. 总体结论

阶段 6 原验收文档把一些“后端/API 已完成”的内容写成了“阶段 6 已通过”，但对照 Roadmap 和实际代码后发现，存在三类问题：

| 类型 | 问题 | 当前状态 |
|---|---|---|
| 用户主链路 | 任务完成后跳转成片详情时误用 `taskId` 当 `videoId` | 已修复 |
| 管理后台 | Roadmap 要求的 `/admin/users`、`/admin/videos` 前端页面缺失 | 已修复 |
| 验收结论 | 文档写“有条件通过”，但 `/admin/quotas` 与真实 E2E 未完成 | 已修正为“部分通过” |

整改后，前端构建已通过：

```bash
cd apps/web
npm run build
```

验证结果：Next.js production build 成功，0 类型错误，0 ESLint 警告。

---

## 2. 已修复问题清单

### 2.1 任务完成后成片预览跳转 ID 错误

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-001 |
| 严重级别 | 高 |
| 问题位置 | `apps/web/src/app/video-tasks/[id]/plans/page.tsx`、`apps/web/src/app/video-tasks/[id]/progress/page.tsx` |
| 原问题 | 页面路由参数 `id` 是 `taskId`，但完成后直接跳转到 `/videos/${id}` |
| 直接后果 | `/videos/:id` 页面调用的是 `/api/videos/{videoId}`，如果传入 `taskId`，成片详情会加载失败或 404 |
| 根因 | 前端把“任务 ID”和“成片 ID”混用；`VideoController.getById()` 接收的是 `videoId` |
| 修复方案 | 任务完成后先调用 `/api/videos?productId=...` 拉取当前商品下的视频列表，再用 `video.taskId === task.taskId` 找到真实 `videoId`，最后跳转 `/videos/{videoId}` |
| 修改文件 | `plans/page.tsx`、`progress/page.tsx` |
| 验证结果 | `npm run build` 通过；代码中不再存在 `/videos/${id}` 的错误跳转 |

修复后的行为：

1. 页面轮询任务状态。
2. 当任务状态进入 `completed` 或 `exported`。
3. 前端根据 `productId` 查询视频列表。
4. 找到 `taskId` 匹配的成片。
5. 使用真实 `videoId` 跳转成片预览页。

---

### 2.2 管理后台用户管理页面缺失

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-002 |
| 严重级别 | 高 |
| 问题位置 | `apps/web/src/app/admin/users/page.tsx` 原本不存在 |
| Roadmap 要求 | `/admin/users` 查看用户列表，依赖 `GET /api/admin/users` |
| 原问题 | OpenAPI 和 Java 后端已经补了 `/api/admin/users`，但前端没有 `/admin/users` 页面，也没有导航入口 |
| 直接后果 | 管理员无法通过 UI 查看用户列表；阶段 6 文档把“后端就绪”写成“通过”不准确 |
| 修复方案 | 新增 `/admin/users` 页面，调用 `/api/admin/users?page=&pageSize=`，展示用户 ID、邮箱、角色、状态、创建时间和分页 |
| 修改文件 | `apps/web/src/app/admin/users/page.tsx`、`apps/web/src/app/admin/layout.tsx`、`apps/web/src/components/nav-sidebar.tsx`、`apps/web/src/types/api.ts` |
| 验证结果 | `npm run build` 输出中已包含 `/admin/users` 路由 |

---

### 2.3 管理后台视频管理页面缺失

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-003 |
| 严重级别 | 高 |
| 问题位置 | `apps/web/src/app/admin/videos/page.tsx` 原本不存在 |
| Roadmap 要求 | `/admin/videos` 查看全部成片，依赖 `GET /api/admin/videos` |
| 原问题 | OpenAPI 和 Java 后端已经补了 `/api/admin/videos`，但前端没有 `/admin/videos` 页面，也没有导航入口 |
| 直接后果 | 管理员无法通过 UI 查看全量成片；阶段 6 管理后台验收不完整 |
| 修复方案 | 新增 `/admin/videos` 页面，调用 `/api/admin/videos?page=&pageSize=&status=`，展示视频 ID、任务 ID、标题、时长、状态、质量分、风险分和预览入口 |
| 修改文件 | `apps/web/src/app/admin/videos/page.tsx`、`apps/web/src/app/admin/layout.tsx`、`apps/web/src/components/nav-sidebar.tsx`、`apps/web/src/types/api.ts` |
| 验证结果 | `npm run build` 输出中已包含 `/admin/videos` 路由 |

---

### 2.4 管理后台导航不完整

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-004 |
| 严重级别 | 中 |
| 问题位置 | `apps/web/src/components/nav-sidebar.tsx`、`apps/web/src/app/admin/layout.tsx` |
| 原问题 | 管理员侧边栏和 admin 子导航缺少用户管理、视频管理入口 |
| 直接后果 | 即使页面存在，管理员也无法从常规 UI 工作流进入 |
| 修复方案 | 在主侧边栏和 admin 二级导航中加入 `/admin/users`、`/admin/videos`；同时整理原有损坏编码文案为稳定英文标签 |
| 修改文件 | `nav-sidebar.tsx`、`admin/layout.tsx` |
| 验证结果 | `npm run build` 通过，路由均可被 Next.js 编译 |

---

### 2.5 阶段 6 验收文档结论过度乐观

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-005 |
| 严重级别 | 中 |
| 问题位置 | `docs/Phase6-Results-And-Acceptance.md` |
| 原问题 | 文档写“有条件通过，可进入 V1 交付”，但同时承认 12 个集成测试未执行、真实 E2E 未验证、额度管理未完成 |
| 直接后果 | 验收结论和 Roadmap 标准不一致，容易误判项目已经具备 V1 交付条件 |
| 修复方案 | 将结论改为“部分通过，仍有 V1 阻塞项”；补充 `/admin/users`、`/admin/videos` 已完成；保留 `/admin/quotas` 和真实 E2E 作为未完成项 |
| 修改文件 | `docs/Phase6-Results-And-Acceptance.md` |
| 验证结果 | 文档中不再保留“有条件通过”的最终结论 |

---

### 2.6 视频库页面 Hook 依赖警告

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-006 |
| 严重级别 | 低 |
| 问题位置 | `apps/web/src/app/videos/page.tsx` |
| 原问题 | `useEffect` 调用 `load()`，但 `load` 未纳入依赖，构建时产生 `react-hooks/exhaustive-deps` 警告 |
| 直接后果 | 当前不会阻塞构建，但会污染验收输出，也可能隐藏后续状态同步问题 |
| 修复方案 | 使用 `useCallback` 包装 `load`，并把 `load`、`page` 纳入 `useEffect` 依赖 |
| 修改文件 | `apps/web/src/app/videos/page.tsx` |
| 验证结果 | `npm run build` 不再输出 ESLint 警告 |

---

## 3. 当前仍未完成的问题

### 3.1 管理后台额度管理未实现

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-007 |
| 严重级别 | 中 |
| Roadmap 要求 | `/admin/quotas` 管理用户额度，依赖 admin quota API |
| 当前状态 | 未完成 |
| 缺失内容 | OpenAPI 缺少 `/api/admin/quotas`、`PATCH /api/admin/quotas/{userId}`；Java 后端未实现；前端页面未实现 |
| 当前处理 | 不凭空新增前端页面，避免前端调用不存在的契约 |
| 建议方案 | 要么补齐 OpenAPI + 后端 + 前端，要么在 Roadmap 中明确将额度管理移出 V1 |

---

### 3.2 真实端到端 E2E 仍需复验

| 项目 | 内容 |
|---|---|
| 问题编号 | P6-008 |
| 严重级别 | 中 |
| Roadmap 要求 | 正向流程可从商品上传跑到 MP4 预览和导出 |
| 当前状态 | 文档层面仍未完成完整验收 |
| 需要验证的链路 | 前端创建商品/任务 → Java API → Python AI Orchestrator → Render Worker → Java callback → 视频库/成片预览/导出 |
| 当前处理 | 阶段 6 文档已改为“不建议进入 V1 交付阶段” |
| 建议方案 | 使用真实商品数据执行一次完整 E2E，并把任务 ID、视频 ID、日志、输出 MP4 URL 写入验收文档 |

---

## 4. 修改文件汇总

| 文件 | 修改内容 |
|---|---|
| `apps/web/src/app/video-tasks/[id]/plans/page.tsx` | 修复完成状态下成片跳转，使用真实 `videoId` |
| `apps/web/src/app/video-tasks/[id]/progress/page.tsx` | 修复完成状态下成片跳转，使用真实 `videoId` |
| `apps/web/src/app/admin/users/page.tsx` | 新增管理用户列表页面 |
| `apps/web/src/app/admin/videos/page.tsx` | 新增管理视频列表页面 |
| `apps/web/src/app/admin/layout.tsx` | 补齐 admin 子导航入口 |
| `apps/web/src/components/nav-sidebar.tsx` | 补齐管理员侧边栏入口，整理导航 |
| `apps/web/src/app/videos/page.tsx` | 修复 Hook 依赖警告 |
| `apps/web/src/types/api.ts` | 增加 admin users/videos 列表数据类型导出 |
| `docs/Phase6-Results-And-Acceptance.md` | 修正验收结论和未完成项 |

---

## 5. 验证结果

### 5.1 前端构建

```bash
cd apps/web
npm run build
```

结果：

| 验证项 | 结果 |
|---|---|
| Next.js production build | 通过 |
| TypeScript 类型检查 | 通过 |
| ESLint 构建警告 | 0 |
| `/admin/users` 路由 | 已生成 |
| `/admin/videos` 路由 | 已生成 |
| `/video-tasks/[id]/plans` 路由 | 已生成 |
| `/video-tasks/[id]/progress` 路由 | 已生成 |
| `/videos/[id]` 路由 | 已生成 |

---

## 6. 最终建议

当前阶段 6 可以认为完成了大部分前端收尾和管理后台查看能力，但还不能按“全部通过”关闭。

建议下一步按下面顺序处理：

1. 决定 `/admin/quotas` 是否进入 V1。
2. 如果进入 V1，先补 OpenAPI，再补 Java 后端，最后补前端页面。
3. 执行真实 E2E：从商品上传到 MP4 预览和导出。
4. 将 E2E 的任务 ID、视频 ID、日志和最终视频 URL 记录回 `Phase6-Results-And-Acceptance.md`。
5. 完成后再把阶段 6 结论从“部分通过”改为“通过”或“有条件通过”。

