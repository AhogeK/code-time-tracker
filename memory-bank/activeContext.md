# Active Context

> 当前工作上下文（保留最近30天，删除>90天前）

## 已归档（A 阶段 + B 早期，详见 archive）

- 2026-08-26 ~ 2026-08-27 的历史条目（A1-A4 基础设施、规则更新、SKILL_GRAPH 创建、AGENTS 重构、B1-B4 早期）已归档至 `memory-bank/archive/2026-08-29-activecontext-a-and-b-early.md`（原文保留）

## [2026-08-27] - B 阶段变更追踪补全：getDirtySessions（版本 0.16.0）

- **B 阶段目标"建立脏数据追踪"补全**：SessionRepository 加 `getDirtySessions()`（is_deleted=0 AND is_synced=0，识别待同步本地变更，C 阶段 push 前置）
- **重构**：行映射提取 `ResultSet.toCodingSession()` helper（getSessions 复用，消除重复）
- 测试 +1（getDirtySessions：返回未同步会话 + 标记同步后为空）；63/63
- **B 阶段最终状态**：B1 DTO 对齐 ✅ / B2 字段映射 + isSynced 脏标记 ✅ / B3 软删（用户否决，不实施）✅ 取消 / B4 设备注册（后端 v0.48.0 + 插件适配）✅ / 变更追踪（脏会话查询）✅
- 版本 0.15.0 → 0.16.0（新功能 MINOR）

## [2026-08-29] - 配置缓存兼容修复 + 设备状态 UI 调整（版本 0.16.1）

- **generateSyncConfig 配置缓存修复**（build.gradle.kts）：
  - 根因：doLast 闭包引用脚本顶层 val（Kotlin 编译成脚本实例字段访问），捕获 Gradle 脚本对象，配置缓存无法序列化；执行期访问 project.version 同样不支持
  - 修复：改为自定义任务类 `GenerateSyncConfig`（@Input webUrl/serverUrl/appVersion + @OutputFile），配置期捕获 `project.version`（syncAppVersion），执行期只访问任务属性
  - 验证：默认配置缓存构建 BUILD SUCCESSFUL（无 problems found），63/63
- **设备注册状态 UI 调整**（SyncSettingsConfigurable）：Device registered 从 Test connection 结果下方移到 Bound([api key]) 右侧（statusRow 并排）——状态与操作反馈分离
- 版本 0.16.0 → 0.16.1（bug 修复 PATCH）

## [2026-08-29] - C 阶段第一批：同步核心（Pull/Push 客户端 + 游标 + 编排，版本 0.17.0）

- **契约缺口（硬阻塞，需求报告）**：ctt-server `SyncChangeDto` 缺 `sessionUuid`（只有服务端主键 `sessionId`），插件无法按本地 `session_uuid` 唯一键匹配/新建会话 → `.omp/ctt-server-syncchange-sessionuuid-requirement.md`（已交用户，待 ctt-server 落地）；插件侧 SyncChangeDto 已按需求契约加 sessionUuid 字段（默认 null，未落地时 applier 跳过）
- **C3 游标持久化**：`sync_cursor` 表迁移（MigrationManager，R15 新表 IF NOT EXISTS）+ `SyncCursorRepository`（getPullCursor 首次 0 / setPullCursor 单调不后退 / setPushAt）
- **C2/C1 传输**：`SyncApiService.pull`（POST /api/v1/sync/pull）+ `push`（POST /api/v1/sync/push）+ SyncApiServiceImpl 实现
- **C1/C4 应用**：`SyncSessionApplier`（UPSERT 新建/覆盖 clean/跳过 dirty；DELETE 软删 clean/保留 dirty；sessionUuid null 跳过）；`SessionRepository` 加 findBySessionUuids（活跃行）/upsertSyncedSession（ON CONFLICT 不复活已删行）/markSynced/markDeleted；DatabaseManager 暴露 getSessionRepository/getSyncCursorRepository
- **编排**：`SyncCoordinator`（@Service，syncOnce：pull→apply→push(markSynced)→pull 收敛；失败保留游标/脏标记幂等重试；429 退避走 SyncHttpClient）；绑定成功后自动初始同步（SyncSettingsConfigurable.registerDeviceOnBind 成功回调）
- **测试**：+19（SyncCursorRepository 4 / SyncSessionApplier 8 / SyncCoordinator 5 / SyncHttpClient pull+push 2）；82/82；配置缓存兼容
- **验收阻塞**：端到端（push→pull→再 pull 空）需 ctt-server 落地 sessionUuid 后才能完整验证；当前集成验证仅网络层可达
- **双轴审查修复**（code-review，Standards+Spec）：① 时间解析 fallback 弃用 LocalDateTime.MIN（会污染 lastModified 破坏 LWW 排序）→ 新建行回退 now、覆盖行保留原值 + fallback 测试；② SyncCoordinator 删除编号步骤注释（KDoc 已文档化流程）+ KDoc 精确化（push 成功即 markSynced，pull#2 失败由下次 pull 补偿）；③ SyncSessionApplier.applier 改默认参数消除无参构造重复 getSessionRepository；④ SessionRepository.findBySessionUuids KDoc 修正（active rows，与 SQL 一致）；⑤ 集成测试补 2 个真实 pull/push 端点用例（无 key 实测 403 非 401，断言 401/403）
- 测试：85/85（+6：真实端点 2 + applier fallback 1 + 审查期新增）；配置缓存兼容
- 版本 0.16.1 → 0.17.0（新功能 MINOR）

## [2026-08-29] - C 阶段端到端真实验收通过（版本 0.17.0）

- **ctt-server v0.49.0 落地 sessionUuid 契约**（需求报告闭环）：SyncChangeDto 加 sessionUuid（服务端主键后、nullable，物理清除的 DELETE 为 null）；SyncPullService.toChangeDto 两处带出；全量 1186 测试通过；dev-docs/sync/frontend-integration.md 确认插件以 sessionUuid 为本地主键（sessionId 不参与匹配，与插件实现一致）
- **真实验收（runIde + 本地 ctt-server v0.49.0）**：
  - 初始同步：3198 个历史会话全量 push + pull 拉回 3198 条 change（含 sessionUuid）+ 游标推进 3198 + 脏标记全清（is_synced 全 1）
  - 幂等：第二次同步只推新产生的 2 个会话，历史零重复，游标单调 3198→3200
  - 重启续传：游标 3200 跨重启保持，设置页 checkDeviceRegistration 正常执行
  - 日志：SyncCoordinator "Sync round completed for device <id>"（与 curl 设备 id 一致）
- **验收结论**：C 阶段验收标准全部达成（push→pull→再 pull 空 + 游标断点续传 + sessionUuid 契约 + 幂等）
- **收尾**：progress.md 更新（B/C 完成）；README 功能描述待更新（云同步 + Privacy 修正）；C 阶段设计文档待写

