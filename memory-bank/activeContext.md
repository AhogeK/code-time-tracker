# Active Context
## [2026-11-06] - pull 失败页重放 + 响应 gzip（版本 0.22.0）

- **问题**：服务端交付页之前就推进 watermark，普通 pull 的 `queryCursor = max(watermark, client)` 会跳过客户端应用失败的页——数据丢失；插件侧无页级重试（apply 失败即结束本轮），下一轮也拿不回该页
- **重放**：`SyncPullRequest.replayFromChangeId` + `SyncCoordinator.pullPage`，每次 pull 恒发本地游标（服务端 `min(replay, watermark)` 读取、`nextCursor` 不回卷）；进程被杀也能续上，无需新增持久化状态
- **兼容**：旧服务端不识别该字段（Jackson 2 默认 400 / Jackson 3 忽略）→ 同轮回退为不带字段的请求；只有 400 消息点名 `replayFromChangeId` 才暂停重放，且 1 小时后自动重探（混部发布不会永久失能），其它 400 只回退
- **收口**：首拉/翻页/reconcile 统一走 `pullPage` → `sendPull`，吊销自愈（404 COMMON_002 → 重注册 → 重试同一请求）覆盖所有取页点（此前只有首拉有）
- **gzip**：每个请求带 `Accept-Encoding: gzip`；按响应 `Content-Encoding` 解压 + UTF-8 解码；解压失败归 UNKNOWN。首次全量 53 MB → 约 7 MB（稳态页 <2 KB 不压缩）
- **测试**：+8（重放起点、400 回退、探测过期重试、翻页吊销自愈、请求体字段出现/缺省、gzip 解析、Accept-Encoding、损坏 gzip 归类），147/147
- **构建确定性**：签名变更后旧测试字节码会从构建缓存/增量编译还原（`rm -rf build` 无效）→ `compileTestKotlin` 不进缓存 + `kotlin.incremental=false`（`--info` 验证 NON_INCREMENTAL_COMPILER）；R21 与 practices 已记录
- **审查修复**：README 折叠块缩进回归、普通 pull 请求构造三处重复、变量命名与"每进程一次"文档不符、缺请求体序列化断言——均已修
- **服务端事实**：replay 字段仅存在于 `../ctt-server` 工作树（HEAD 无），报告所称 v0.81.0 与本地 0.92.0 不符——发布版本待服务端确认；插件对未升级服务端无回归
- 版本 0.21.2 → 0.22.0（MINOR，协议能力新增）

## 已归档（2026-08-27 ~ 2026-09-26）

> 见 `memory-bank/archive/2026-11-06-activecontext-2026-08-09.md`（原文保留，R16）

## [2026-10-26] - .omp 产物归档：按类型 + 日期分目录（无代码变更）

- 根目录 14 个文件 → `delivery|requirements|qa/<YYYY-MM-DD>/`（日期取 mtime），移动前后逐文件核对字节数与 SHA-256 全一致；新增 `.omp/README.md`（布局/命名唯一权威 + 旧→新迁移表）；AGENTS R30 与 activeContext 的 `.omp/` 引用同步；`memory-bank/archive/` 旧路径保持不变（R16 只写不改）；顺带按提交日校正三条记忆日期（0.21.x=10-26、0.20.4=09-26、规则吸收=09-15）

## [2026-10-26] - 会话级 IDE 元数据（0.21.0 发送 / 0.21.1 pull 侧补齐）

- **需求（ctt-web）**：IDE 从"设备注册展示字段"改为每条会话自带：`CodingSession.ideVersion` + SQLite `ide_version`（幂等迁移）+ `SyncSessionMapper` 逐会话发送 `ideName/ideVersion`；deviceId 与注册路径不变
- **push（0.21.0）**：模型、MigrationManager、SessionRepository（8 处 SQL/映射）、TimeTrackerService、DataExportImportService（报告未覆盖：导出/导入需携带，否则丢字段）、SyncSessionDto、Mapper；`ide_name/ide_version` 属会话身份字段（`ON CONFLICT` 不更新）
- **pull（0.21.1）**：`SyncChangeDto` 增可选 `ideName/ideVersion`；applier 新建行优先采用服务端值、缺失回退本地——服务端未上线时行为不变
- **服务端**：当时未实施（DTO/表无 IDE 字段）。2026-11-06 更正：当时"Jackson 忽略未知字段"是推断而非验证——服务端若由 Jackson 2 默认 mapper 反序列化会 400，Jackson 3 才默认忽略；插件现对 400 做回退（见 0.22.0 条目）
- **测试**：+8（push 6 + pull 2），红-绿通过，138/138；坑：Kotlin 签名变更后 `clean test` 仍可能命中陈旧 class（`NoSuchMethodError`）→ 当时用 `rm -rf build`，2026-11-06 更正为 `--no-build-cache --rerun-tasks`（缓存会放回旧测试类）
- **迁移加固（0.21.2）**：加列改为"先查列 → 缺失才 ALTER → 被拒后复查（并发 IDE 场景）→ 仍缺失必抛出"；只读库测试证明加列失败不再被静默吞掉；测试 139/139
- 版本 0.20.4 → 0.21.0（MINOR）→ 0.21.1（PATCH，补完 pull 侧）→ 0.21.2（PATCH，迁移加固）；R21 增"签名变更后陈旧编译"预防；`.idea/*` 删除属外部改动，已恢复不入提交
