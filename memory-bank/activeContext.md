# Active Context
## [2026-08-29] - AGENTS.md R26 升级 v3：网络检索多工具可选（用户决策）

- 背景: 用户解除"禁用内置 WebSearch"限制——WebSearch 与浏览器类技能（doko/opencli/browser-use）均可使用、按场景配合
- 变更: R26 v2 → v3：删"禁止使用内置 WebSearch 工具"；改为"网络检索可选用内置 WebSearch 或浏览器类技能/工具链，按场景择优，允许配合使用"；保留浏览器安全红线（用户浏览器、禁 Incognito）与高级 AI 咨询（gemini/perplexity）
- 顺带: 删除 R26 末尾与 275 行重复的遗留行（"可使用skills访问 gemini..."）
- 同步: 全局 ~/.omp/agent/AGENTS.md 4.2 同批改写为"网络检索工具选择"（多工具可用）
- 状态: ✅ 完成，待提交

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


## [2026-08-29] - D 阶段批次 1：调度与集成核心（版本 0.18.0）

- **触发策略（判断）**：手动（SyncScheduler.syncNow 统一入口）+ 定时兜底（可配置间隔，默认 5 分钟）+ IDE 关闭前 flush（AppLifecycleListener.appWillBeClosed）+ 绑定后初始同步（既有）；**会话结束与项目打开不做显式触发**——每次空闲持久化（60s）就同步 = 频繁网络请求 + 侵入追踪核心；项目打开同步已试做（ProjectManagerListener.projectOpened）但 2026.1 将其 @Deprecated(forRemoval=true) 且无替代 open 事件，IDE inspection 无法用 Kotlin @Suppress 压制（Java 侧检查），故移除该触发点，由定时兜底（5 分钟）覆盖
- **SyncScheduler**（新 @Service，Disposable）：单线程 daemon ScheduledExecutor，scheduleWithFixedDelay（固定延迟 = 间隔语义）；`start()` 启动、`reschedule()` 按 settings.syncIntervalMinutes 重排（0 或未启用 = 停表）、`syncNow()` 后台统一触发入口（EDT/事件/生命周期安全）
- **SyncCoordinator 并发锁**：AtomicBoolean CAS 防重入（重叠触发 no-op）；状态记录 `lastSyncAt`/`lastSyncError`（@Volatile，设置页展示用，成功清错误）
- **SyncSettingsState**：+syncIntervalMinutes（默认 5，0 关闭定时，coerceAtLeast(0)）
- **SyncLifecycleListener**（新 listener）：appFrameCreated → scheduler.start()；appWillBeClosed → syncNow()（best-effort flush；脏会话已落本地，中断只延迟下次启动补传）——2026.1 API 坑：AppLifecycleListener 在 `com.intellij.ide` 包；appStarted/beforeAppWillBeClosed 标 @ApiStatus.Internal（不可 override）→ 用 appFrameCreated 启动（非 Internal）；关闭方法名 appWillBeClosed(boolean)（无 appWillExit）
- **ProjectCloseListener 拆分**：平台 2026.1 将 ProjectManagerListener 全方法 @Deprecated(forRemoval=true)；关闭侧有替代（com.intellij.openapi.project.ProjectCloseListener 新 API），打开侧无替代 → 拆成 `ProjectCloseTrackingListener`（新 API，projectClosing 停追踪）+ `ProjectSyncListener`（旧 API，已删除）；项目打开同步由定时兜底覆盖
- plugin.xml 注册 SyncLifecycleListener
- **测试**：+4（SyncScheduler 3：syncNow 后台触发/未启用停表/间隔 0 停表；SyncCoordinator 并发锁 1：重叠触发 no-op 只跑一轮）；89/89；配置缓存兼容
- 版本 0.17.0 → 0.18.0（新功能 MINOR）

## [2026-08-29] - D 阶段批次 2：设置页同步 UI（版本 0.19.0）

- **Sync now 手动按钮**：actionPanel 第三按钮（Unbind / Test connection / Sync now）；未绑定/未启用时提示先绑定；后台 coordinator.syncOnce()（拿结果反馈成功/失败）；busy 期间禁用 + 绑定感知
- **同步状态行**（syncStatusLabel）：`Last sync: HH:mm:ss（或 Never synced） • Pending: N • Last error: <msg>`——lastSyncAt/lastSyncError 来自 SyncCoordinator（批次 1 状态），Pending = getDirtySessions().size（后台线程读 DB）；错误时红色；刷新时机：打开设置页 / 绑定解绑后 / Sync now 后 / apply 后
- **间隔配置**（syncIntervalField，分钟，0=off）：isModified/apply/reset 接线（非法回默认 5）；apply 里 scheduler.reschedule() 使新间隔/开关即时生效（不重启）
- **实现细节**：LocalDateTime.format 用 java.time 自带（无冗余 extension）；待同步数用 DatabaseManager.getSessionRepository（后台线程）
- 测试 90/90（UI 无单测，手动验收）；配置缓存兼容
- 版本 0.18.0 → 0.19.0（新功能 MINOR）
- **D 阶段状态**：D1 触发（批次 1）✅ / D2 状态展示（批次 2）✅ / D3 后台执行（批次 1）✅ / D4 配置持久化（批次 1 间隔字段 + 批次 2 UI）✅

## [2026-08-29] - 设置页布局 + 持久化 lastSync + 设备 id 一致性 + 换绑隔离（版本 0.19.1）

- **设置页布局重排（用户反馈驱动）**：Test connection 移到服务器地址旁（它测编辑中的地址）；Bind/Unbind 配对同行（API key 生命周期）；Sync now 移入同步区（间隔输入右侧，一行紧凑）；serverUrlField 40→24 列、syncIntervalField 绑定 maximumSize=preferredSize（防 FormBuilder/BoxLayout 拉伸到全宽，4 列）
- **lastSyncAt 持久化（bug）**：原为 SyncCoordinator 内存字段（重启即清空 → 设置页显示 Never synced，即使 C 阶段同步过）；改从 `sync_cursor.last_push_at` 读（SyncCursorRepository.getLastPushAt，跨重启保留）；syncNow 成功反馈不再写中间操作状态行（改由同步状态行 Last sync 推进 + 失败走右上角通知）
- **设备 id 跨 IDE 一致性（bug，用户报告）**：原 UserManager.getUserId() 依赖 coding_sessions 有数据才跨 IDE 共享（DB 空时各 IDE 生成不同 id → 同一台电脑双 IDE 变 2 设备）；新增 `app_user` 表（MigrationManager R15）+ DatabaseManager.getOrCreateUserId（app_user → coding_sessions 旧数据迁移 → 生成写入），UserManager 改用共享 DB（与会话表无关，首次绑定也一致）
- **换绑用户隔离（bug，用户报告）**：换绑 A→B key 时原会把 A 的脏会话推给 B（泄露）+ 用 A 的旧游标跳过 B 的前 N 条 change（丢数据）；新增 SyncCoordinator.resetForUserSwitch（清本机 sync_cursor + markAllSynced 旧会话标记不推新用户），绑定流程检测换绑（wasBound）触发；新会话正常同步到新用户
- **统计账号隔离（bug，用户报告）**：原统计查本地全部会话，换绑后 A/B 用户数据混算；后端已存在 `GET /api/v1/users/me`（SYNC key 可调，返回 data.id，需求反馈确认无新端点）；本地 `coding_sessions` 加 owner_user_id 列（R15）+ CurrentUserResponse/SyncApiService.currentUser + SyncSettingsState.serverUserId；push 成功/pull 应用标记 owner；StatsRepository 全部查询 + SessionRepository 统计查询（getRecordCount/getAllActiveSessionTimes/getFirstRecordDate）按 owner 过滤（内部 ownerUserId 字段，DatabaseManager.setStatsOwner 同时设置两 repo）；绑定成功调 me → serverUserId + setStatsOwner，换绑 resetForUserSwitch 清 owner；未绑定全量、绑定后只当前用户数据、导出（getSessions）不过滤
- **测试**：+5（clear 游标/时间、markAllSynced、resetForUserSwitch、getLastPushAt ×2、stats owner 过滤、markSynced owner 写入）；97/97；配置缓存兼容
- 版本 0.19.0 → 0.19.1（bug 修复 + UI 改进 PATCH）

[2026-08-29] - Fix account isolation statistics:
  - Unbind-then-bind account switch now correctly resets pull cursor (was skipped when wasBound=false)
  - Bind-account statistics include locally uncommitted sessions (owner_user_id IS NULL) so new coding shows up immediately
  - Fix SQL syntax error (missing leading space → "0AND") that broke record count
  - Move device registration UserManager.getUserId() off EDT to avoid UI freeze
  - Fix inline FQN for java.sql.* and java.util.UUID to correct imports

[2026-08-29] - E phase: end-to-end cloud sync verification and documentation:
  - Verified full end-to-end LWW conflict resolution (last-write-wins)
  - Verified error recovery: network outage/429 backoff/invalid auth all fail-safe without data loss
  - Updated README.md with complete cloud sync setup instructions and conflict resolution notes
  - Verified account switching logic works correctly: unbind→bind correctly resets pull cursor and marks previous user's dirty sessions
  - Verified statistics filtering correctly includes bound user + local uncommitted sessions

[2026-08-29] - E phase gap fixes (0.19.3):
  - E1: added SyncConvergenceTest (4 scenarios): dual-device create/edit/delete/LWW-conflict convergence, in-memory ctt-server replica
  - E2: added revoked-key re-bind prompt test (AUTH_012 -> toUserMessage), network-failure-retry-then-converge test
  - E3: added README Cloud Sync Troubleshooting section (7 symptoms + LWW conflict explanation), fixed duplicated "Requests."
  - Test isolation: UserManager.setUserIdForTest (internal), SyncCoordinator deviceMetadataProvider + notifySyncCompleted injection points; SyncCoordinatorTest no longer requires IDE Application (was order-dependent on LightPlatformTestCase)
  - Version bumped 0.19.2 -> 0.19.3 (PATCH)
