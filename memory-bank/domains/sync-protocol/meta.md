# sync-protocol — meta

## Boundary

插件侧的双向同步引擎：与服务端 `/api/v1/sync/**` 的握手（设备注册、key 绑定）、拉取与应用远端变更、推送本地脏会话、游标管理，以及该协议对本地数据施加的约束。

**In scope**：冲突应用规则（LWW 的客户端侧）、游标语义、服务端分页循环、批量 upsert 与墓碑、失败语义（幂等/退避/自愈）、账号隔离（owner scope）、调度触发。

**Out of scope**：服务端裁决实现（`../ctt-server`，只读）、统计口径（`stats-aggregation`）、设置页 UI 样式、追踪引擎如何产生会话（产品行为见 `src/` 代码）。

## Owned paths

- `src/main/kotlin/com/ahogek/codetimetracker/service/sync/` — 全部同步类（Coordinator/Applier/DTO/HTTP/调度/错误映射）
- `src/main/kotlin/com/ahogek/codetimetracker/database/SyncCursorRepository.kt` — 游标持久化
- `src/main/kotlin/com/ahogek/codetimetracker/database/SessionRepository.kt` — 同步写路径（`upsertSyncedSessions` / `markSynced` / `markDeleted` / `getDirtySessions`）
- `src/main/kotlin/com/ahogek/codetimetracker/database/MigrationManager.kt` — `sync_cursor` 表
- 测试：`src/test/kotlin/com/ahogek/codetimetracker/service/sync/`、`.../database/SyncCursorRepositoryTest.kt`

## Where to start

1. `service/sync/SyncCoordinator.kt` — 一轮的编排（pull → push → reconcile pull）与分页循环
2. `service/sync/SyncSessionApplier.kt` — 应用规则（UPSERT/DELETE、dirty 跳过、连续段批处理）
3. `database/SessionRepository.kt` — 批量 upsert SQL 与墓碑 lifting
4. `service/sync/SyncHttpClient.kt` + `SyncApiServiceImpl.kt` — 传输、envelope、429、错误映射
5. `database/SyncCursorRepository.kt` — 游标单调不后退
6. `service/sync/SyncScheduler.kt` + `SyncLifecycleListener.kt` — 触发（定时 5min 兜底 / 生命周期 / 手动）

## Terminology

| 术语 | 含义 |
|---|---|
| **LWW** | Last-write-wins：服务端裁决链 delete wins → serverVersion → clientVersion → clientModifiedAt |
| **change log** | 服务端 `session_changes`：每用户追加式、changeId 单调 |
| **watermark / 游标** | 插件侧 `sync_cursor.last_pulled_change_id`（单调不后退）；服务端也持久化自己的 watermark |
| **page / hasMore** | 一次 pull 返回一页（服务端 `ctt.sync.pull-batch-size` 默认 1000），`hasMore=true` 表示还有 |
| **dirty 行** | `is_synced = 0` 的本地会话——有未推送修改，pull 不覆盖 |
| **tombstone / 软删** | `is_deleted = 1`；服务端 upsert 会 lifting 它（服务器快照是权威活状态） |
| **revoke self-heal** | 404 `COMMON_002` → 重注册设备 → 重试一次（无需重绑 key） |
| **owner scope** | `owner_user_id`：换绑账号后统计/推送按当前账号隔离 |

## Verification baseline

| | |
|---|---|
| 插件侧核对 | 2026-09-03 · v0.20.1（本领域文件与 `src/` 代码同批写入） |
| 服务端契约核对 | 2026-09-03 · `../ctt-server` 工作区 v0.73.0 源码（`SyncPullService` / `SyncPullResponse` / `SyncProperties` / `SyncPullPagingIntegrationTest`）；分页特性自 v0.62.0 引入 |
| 覆盖 | 插件实现 + 服务端 pull/push 契约；插件测试 119/119（含 sync 套件） |
| 已知漂移 | 未逐条复核——依赖具体端点或字段前先回源 `../ctt-server` 与 `src/` |

未核验部分当作线索，不当作事实。
