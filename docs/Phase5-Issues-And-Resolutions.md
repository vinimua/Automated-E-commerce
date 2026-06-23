# Phase 5 问题与解决方案汇总

---

## 0. 文档元信息

| 项目 | 内容 |
|---|---|
| 项目名称 | TikTok Shop AI 带货视频生成系统 |
| 阶段编号 | Phase 5 |
| 阶段名称 | Node Render Worker |
| 文档用途 | 记录阶段 5 验收发现的问题、修复方案、验证结果和剩余风险 |
| 生成日期 | 2026-06-18 |
| 关联阶段文档 | `docs/Phase5-Implementation-Documentation.md` |
| 当前结论 | 核心高风险问题已整改，阶段 5 可有条件通过；真实 Remotion 渲染、COS 上传和完整 E2E 仍需补验 |

---

## 1. 总体结论

阶段 5 的目标是实现 Node.js + Remotion + FFmpeg 渲染 Worker，包括 RabbitMQ 消费、RenderManifest 校验、素材下载、视频渲染、封面生成、COS 上传和 Java 渲染回调。

验收时发现的问题主要集中在 5 类：

| 类型 | 说明 | 当前状态 |
|---|---|---|
| 渲染正确性 | Remotion Composition 固定 900 帧，导致非 30 秒视频时长错误 | 已修复 |
| 契约一致性 | Worker 校验过松，Python RenderManifest 与 schema 存在枚举和 `videoId` 差异 | 已修复 |
| 安全问题 | `.env` 包含真实 COS 密钥，SSRF 只校验 hostname 字面值 | 已修复 |
| 队列可靠性 | 渲染/COS/callback 失败后可能无限 requeue | 已修复 |
| 联调配置 | Java 默认 callback URL 指向远程地址，本地联调容易回调错服务 | 已修复 |

整改后已完成 TypeScript 编译、RenderManifest validator 正反例、Python RenderManifest 实例化和 Java 编译验证。尚未完成真实 Remotion 渲染、COS 上传和 Java → RabbitMQ → Render Worker → Java callback 的完整 E2E。

---

## 2. 已修复问题清单

### 2.1 Remotion 渲染时长固定为 30 秒

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-001 |
| 严重级别 | 高 |
| 问题位置 | `apps/render-worker/src/remotion/root.tsx` |
| 原问题 | 3 个 Composition 固定 `durationInFrames=900` |
| 直接后果 | 15 秒、20 秒、25 秒任务也会按 30 秒渲染，视频时长与 RenderManifest 不一致 |
| 修复方案 | 增加 `calculateVideoMetadata()`，按 `manifest.duration * fps` 动态计算帧数 |
| 验证结果 | `npx tsc --noEmit` 通过 |

---

### 2.2 RenderManifest 校验过松

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-002 |
| 严重级别 | 高 |
| 问题位置 | `apps/render-worker/src/services/manifest-validator.ts` |
| 原问题 | 旧校验只检查少量字段，没有严格对齐 `docs/04-render-manifest-schema.md` |
| 直接后果 | 结构不完整或契约错误的 manifest 可能进入渲染阶段，错误延迟暴露 |
| 修复方案 | 重写校验器，覆盖 required fields、UUID、枚举、assets、edit、subtitleStyle、music、cover、output 和 duration 一致性 |
| 验证结果 | validator 正例通过，空 `videoId` / 缺失 `output` 的反例失败 |

关键校验项：

| 校验项 | 当前行为 |
|---|---|
| `manifestVersion` | 必须为 `1.0.0` |
| `taskId` / `videoId` | 必须为 UUID |
| `template` | 只允许 V1 三个模板 |
| `assets` | 4-12 个，`shotNo` 唯一 |
| 非 text 素材 | 必须有 http(s) URL |
| text 素材 | 必须有 `textContent` |
| `duration` | 与 assets duration 总和误差不超过 1 秒 |
| `cover.sourceShotNo` | 必须匹配已有 asset shotNo |

---

### 2.3 SSRF 防护不足

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-003 |
| 严重级别 | 高 |
| 问题位置 | `apps/render-worker/src/utils/ssrf-protection.ts`、`apps/render-worker/src/services/asset-downloader.ts` |
| 原问题 | 只检查 URL hostname 和白名单，没有校验 DNS 解析后的 IP |
| 直接后果 | 白名单域名如果被解析到私网地址，仍可能触发 SSRF 风险 |
| 修复方案 | 增加 `validateResolvedUrl()`，对 DNS lookup 结果逐个检查，拒绝私网、回环、链路本地等 IP |
| 验证结果 | TypeScript 编译通过 |

---

### 2.4 RabbitMQ 失败后可能无限重入队列

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-004 |
| 严重级别 | 高 |
| 问题位置 | `apps/render-worker/src/consumer/render-consumer.ts` |
| 原问题 | 渲染/COS/callback 失败后使用 `channel.reject(msg, true)` |
| 直接后果 | 永久失败的任务会无限 requeue，占满 Worker，导致后续任务无法处理 |
| 修复方案 | 使用 `x-render-retry-count` header 做有限重试，超过 `MAX_RENDER_RETRIES` 后回调 Java failed 并 `ack` |
| 验证结果 | TypeScript 编译通过 |

修复后的失败语义：

| 场景 | 当前行为 |
|---|---|
| JSON 解析失败 | 直接 reject，不重入队列 |
| Manifest 校验失败 | callback failed 后 ack |
| 单个素材下载失败 | 生成占位图，继续渲染 |
| 渲染/COS/callback 失败 | 有限重试，超过上限后最终失败回调并 ack |

---

### 2.5 Java callback URL 默认值不适合本地联调

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-005 |
| 严重级别 | 中 |
| 问题位置 | `apps/api-java/.../RenderMessageProducer.java`、`apps/api-java/src/main/resources/application.yml` |
| 原问题 | RenderMessageProducer 默认 callback URL 指向远程 `124.223.200.16:8080` |
| 直接后果 | 本地启动 Java 时，Render Worker 可能回调到错误地址 |
| 修复方案 | 默认改为 `http://localhost:8080`，新增 `JAVA_API_BASE_URL` 环境变量配置 |
| 验证结果 | `./gradlew.bat compileJava` 通过 |

---

### 2.6 真实 COS 密钥进入本地 `.env`

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-006 |
| 严重级别 | 高 |
| 问题位置 | `apps/render-worker/.env` |
| 原问题 | `.env` 中包含真实 Tencent COS SecretId / SecretKey |
| 直接后果 | 密钥可能被误提交、复制或泄露，影响云资源安全 |
| 修复方案 | 删除真实 `.env`，新增 `.env.example`，仅保留变量模板 |
| 验证结果 | 当前工作区 `apps/render-worker/.env` 不存在；扫描未发现真实密钥特征 |

安全建议：

```text
如果这些 COS 密钥曾经进入过聊天、截图、提交记录或共享文件，应在腾讯云控制台轮换或禁用旧密钥。
删除本地 .env 只能阻止继续扩散，不能撤销已经暴露的密钥。
```

---

### 2.7 COS 配置缺失时错误不清晰

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-007 |
| 严重级别 | 中 |
| 问题位置 | `apps/render-worker/src/services/cos-uploader.ts` |
| 原问题 | COS 配置为空时，错误会延迟到 SDK 内部才暴露 |
| 直接后果 | 联调时难以快速判断是代码问题、网络问题还是环境变量缺失 |
| 修复方案 | 创建 COS client 前显式检查 `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_BUCKET` |
| 验证结果 | TypeScript 编译通过 |

---

### 2.8 Python RenderManifest `zoom` 枚举与契约不一致

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-008 |
| 严重级别 | 高 |
| 问题位置 | `services/ai-orchestrator/src/schemas/render_manifest.py` |
| 原问题 | Python 端使用 `slight_in/dramatic_in/pull_out`，schema 和 Worker 使用 `slow_in/slow_out/fast_in` |
| 直接后果 | Phase 4 生成的 manifest 会被 Phase 5 Worker 拒绝 |
| 修复方案 | Python `EditConfig.zoom` 改为 `none/slow_in/slow_out/pulse/fast_in` |
| 验证结果 | Python RenderManifest 实例化通过；Worker validator 正例通过 |

---

### 2.9 Python RenderManifest `videoId` 为空字符串

| 项目 | 内容 |
|---|---|
| 问题编号 | P5-009 |
| 严重级别 | 高 |
| 问题位置 | `services/ai-orchestrator/src/schemas/render_manifest.py`、`apps/render-worker/src/consumer/render-consumer.ts`、`RenderCallbackServiceImpl.java` |
| 原问题 | Python `RenderManifest.videoId` 默认为空字符串，但 schema 要求 UUID |
| 直接后果 | Worker 严格校验后会拒绝该 manifest；即使渲染成功，Java 也无法稳定使用同一个 videoId |
| 修复方案 | Python 生成 manifest 时自动创建 UUID；Render Worker 成功回调带回 `videoId`；Java 使用回调中的 `videoId` 创建 video 记录 |
| 验证结果 | Python/TypeScript/Java 编译与运行验证通过 |

---

## 3. 验证证据

### 3.1 TypeScript 编译

| 命令 | 执行目录 | 结果 |
|---|---|---|
| `npx tsc --noEmit` | `apps/render-worker/` | 通过 |

### 3.2 RenderManifest validator 正反例

| 验证项 | 结果 |
|---|---|
| 合法 manifest | `{"valid": true}` |
| 空 `videoId` | `{"valid": false, "error": "videoId must be a UUID"}` |
| 缺失 `output` | `{"valid": false, "error": "output must be an object"}` |

### 3.3 Python RenderManifest 实例化

| 验证项 | 结果 |
|---|---|
| `zoom="slow_in"` | 通过 |
| `videoId` 自动生成 UUID | 通过 |
| `cover.text` / `cover.sourceShotNo` 必填 | 通过 |

### 3.4 Java 编译

| 命令 | 执行目录 | 结果 |
|---|---|---|
| `./gradlew.bat compileJava` | `apps/api-java/` | 通过 |

---

## 4. 当前剩余风险

| 编号 | 风险 | 严重级别 | 当前状态 | 建议 |
|---|---|---|---|---|
| RISK-001 | 真实 Remotion 渲染未执行 | 中 | 未验证 Chromium、bundle、renderMedia 真实运行 | 启动 Render Worker 后用真实 manifest 渲染一次 MP4 |
| RISK-002 | COS 上传未执行 | 中 | SDK 已集成，但未使用真实 COS 环境上传 | 配置 COS env 后验证 MP4 和 cover 可访问 |
| RISK-003 | 完整 E2E 未执行 | 中 | Java → RabbitMQ → Render Worker → COS → Java callback 未全链路跑通 | Phase 6 联调时必须验证 |
| RISK-004 | 最终失败 callback 失败后只记录日志 | 中 | 避免无限重试，但 Java 可能收不到最终失败状态 | 后续可增加死信队列或失败事件表 |
| RISK-005 | 真实 COS 密钥曾出现在本地 `.env` | 高 | 当前文件已删除，但无法撤销历史暴露 | 轮换或禁用旧密钥 |

---

## 5. 当前验收判断

| 验收项 | 当前判断 | 说明 |
|---|---|---|
| RabbitMQ Consumer | 通过 | 已接入有限重试和失败回调 |
| RenderManifest 校验 | 通过 | 已严格对齐契约关键字段 |
| Remotion 模板代码 | 通过 | 编译通过，动态 duration 已修复 |
| SSRF 防护 | 通过 | 增加 DNS 解析结果校验 |
| COS SDK 集成 | 有条件通过 | 代码就绪，真实上传未执行 |
| Java Render Callback | 通过 | callback URL 可配置，Java 编译通过 |
| Python → Worker manifest 契约 | 通过 | `zoom`、`videoId`、`cover` 已对齐 |
| 真实渲染 E2E | 未验收 | 需要 Phase 6 联调环境 |

最终结论：

```text
阶段 5 的代码级高风险问题已经整改完成，可进入有条件通过状态。
正式通过仍需要补充真实 Remotion 渲染、COS 上传和完整 E2E 验证。
这些剩余项不阻塞进入 Phase 6，但必须作为 Phase 6 联调验收的 P0 项。
```

---

## 6. 后续建议

| 优先级 | 建议 | 原因 |
|---|---|---|
| P0 | 轮换 Tencent COS 密钥 | 真实密钥曾出现在本地 `.env` |
| P0 | 启动完整链路做一次 E2E | 证明 Java、RabbitMQ、Worker、COS、callback 闭环成立 |
| P1 | 增加 RenderManifest validator 单元测试 | 防止 schema 演进后 Worker 校验退化 |
| P1 | 增加失败任务死信队列 | 避免最终失败 callback 丢失后无法追踪 |
| P1 | 用真实素材跑一次 15s/20s/25s 视频 | 验证动态 duration 和模板时序 |

