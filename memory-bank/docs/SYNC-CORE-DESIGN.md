# C 阶段设计文档：同步核心（Pull / Push / 游标）

日期：2026-08-29
版本：0.17.0
状态：已实现 + 端到端真实验收通过

## 目标

实现多设备最终一致的会话同步循环：拉取远端增量 → 按操作应用本地 → 推送本地脏会话 → 推进游标。服务端 LWW 冲突裁决，插件侧保证幂等与断点续传。

## 架构与数据流

```
┌──────────────┐   pull(deviceId, lastPulledChangeId)    ┌────────────┐
│  SyncCoordinator │ ────────────────────────────────▶ │ ctt-server  │
│   (syncOnce)     │ ◀──────────────────────────────── │  (v0.49.0)  │
│                  │   push(deviceId, sessions[])      │            │
└──────┬───────┬───┘                                    └────────────┘
       │       │
       ▼       ▼
┌──────────────┐  ┌──────────────┐
│ SessionRepository │  │ SyncCursorRepository │
│ (coding_sessions) │  │ (sync_cursor)         │
└──────────────┘  └──────────────┘
```

一次 `syncOnce()` 的三步（都在后台线程，不阻塞 EDT）：

1. **Pull**：`pull(deviceId, cursor)` → 应用 changes（SyncSessionApplier）→ 持久化 `nextCursor`
2. **Push**：收集 `getDirtySessions()`（is_deleted=0 AND is_synced=0）→ 批量 push → 成功后 `markSynced`
3. **Pull again**：从第 1 步后的游标再拉一次，收敛服务端权威状态（含本机 push 产生的 change 和并发写入）

## 关键设计决策

| 决策 | 理由 |
|---|---|
| **pull 游标只由 pull 响应推进**，push 不推进 | push 响应 `nextCursor` 是"当前最大 changeId"，若用作 pull 起点会跳过本机自己的 change；pull#2 用第 1 步游标能拉回全部新 change（含自己的，幂等覆盖） |
| **push 成功即 markSynced**（pull#2 前） | 服务端整批原子接受，本地标记同步安全；若 pull#2 失败，下次 pull 补偿收敛，不重复 push |
| **pull 应用跳过 dirty 行**（isSynced=false） | 本地未推送修改优先，不丢数据；下次 push 提交本地状态由服务端裁决 |
| **软删行永不复活** | `findBySessionUuids` 过滤 is_deleted=0；upsert 的 ON CONFLICT 不碰 is_deleted |
| **本地无 serverVersion** | LWW 裁决在服务端；本地只需 clientVersion（= syncVersion）+ clientModifiedAt 参与裁决，服务端 echo 胜者 |
| **sessionUuid 为本地匹配键** | changes[] 携带 sessionUuid（服务端 v0.49.0 契约）；sessionId（服务端主键）不参与本地匹配 |
| **游标单调不后退** | `setPullCursor` 用 `MAX(last, excluded)` 守卫，崩溃/乱序响应不会重拉已应用 change |
| **绑定后自动初始同步** | 注册设备成功后 `syncOnce()`；已同步会话不重复上报（幂等） |

## 契约要点（ctt-server v0.49.0）

- `POST /api/v1/sync/pull`：请求 `{deviceId, lastPulledChangeId}`；响应 `{changes[], nextCursor}`；changes 按 changeId 升序，携带完整胜出快照（含 sessionUuid）
- `POST /api/v1/sync/push`：请求 `{deviceId, sessions[]}`；整批事务原子；响应 `{nextCursor}`；`deleted=true` 且服务端无此会话 → 幂等跳过
- LWW 裁决顺序：delete wins → serverVersion → clientVersion → clientModifiedAt；identical 状态 KEEP_EXISTING（幂等 no-op）
- 认证：SYNC scope API key（或 JWT）

## 失败处理与幂等

| 失败点 | 行为 |
|---|---|
| pull 失败 | 游标不推进，下次重拉 |
| push 失败 | 脏标记保留（不 markSynced），下次重推 |
| 429 | SyncHttpClient 按 Retry-After 退避（header 优先，body ISO-8601 兜底，60s 上限） |
| 网络/5xx | 保留状态，下次同步重试 |
| 时间戳解析失败 | 覆盖行保留原值、新建行用 now（不污染 LWW 排序） |

## 测试

- 单测 85/85：游标（4）、applier（9：新建/覆盖/跳过 dirty/软删/保留 dirty/null 跳过/时间回退）、编排（5）、pull/push 端点序列化（2）+ 既有套件
- 真实集成：pull/push 无 key → 401/403（端点可达 + 认证拒绝）
- 真实验收：3198 会话全量同步 → 增量幂等（第二次只推 2 个新会话）→ 游标 3200 跨重启保持

## 边界与后续

- 当前同步触发点：绑定流程（无定时/手动按钮）——D 阶段可加定时调度 + 手动同步 UI
- 本地删除不参与同步（B3 已取消软删；deleted 契约字段保留，本地恒 false）
- 服务端未返回 sessionUuid 的旧契约下，applier 跳过（null 降级），升级后端后自动生效
