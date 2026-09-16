# sync-protocol — references

> 事实查表，不含判断。契约侧以 `../ctt-server` 源码为准（只读，R3）。

## 端点

| 端点 | 用途 | 请求 | 响应 data |
|---|---|---|---|
| `POST /api/v1/sync/pull` | 拉取变更页 | `{deviceId, lastPulledChangeId}` | `{changes[], nextCursor, hasMore}` |
| `POST /api/v1/sync/push` | 推送脏会话批 | `{deviceId, sessions[]}` | `{nextCursor}` |
| `POST /api/v1/devices` | 注册/更新设备（幂等，可清吊销） | `RegisterDeviceRequest` | 设备信息 |
| `GET /api/v1/devices` | 设备列表（含 `revokedAt`） | — | 设备数组 |
| `GET /api/v1/users/me` | 当前账号（SYNC key 可调） | — | `{id, email}` |

## DTO 字段（插件侧 `SyncDtos.kt`）

| DTO | 字段 |
|---|---|
| `SyncPullRequest` | `deviceId: String?`, `lastPulledChangeId: Long = 0` |
| `SyncPullResponse` | `changes: List<SyncChangeDto> = []`, `nextCursor: Long = 0`, `hasMore: Boolean = false` |
| `SyncPushRequest` | `deviceId: String?`, `sessions: List<SyncSessionDto> = []` |
| `SyncPushResponse` | `nextCursor: Long = 0` |
| `SyncChangeDto` | `changeId`, `sessionId?`(服务端主键，不参与匹配), `sessionUuid?`(本地匹配键), `op`(UPSERT/DELETE), `serverVersion`, `happenedAt`, `projectName`, `language`, `startTime`, `endTime`, `clientModifiedAt`, `clientVersion`, `deleted` |
| `SyncSessionDto`（push 用） | `sessionUuid`, `projectName`, `language`, `startTime`, `endTime`, `clientModifiedAt`, `clientVersion`, `deleted` |

时间戳均为 ISO-8601 instant（如 `2026-08-25T10:00:00Z`）；解析失败回退：覆盖行保留原值、新建行用 now。

## 服务端配置

| 键 | 默认 | 说明 |
|---|---|---|
| `ctt.sync.pull-batch-size` | 1000 | 单次 pull 最大 change 数（1–10000） |

## Push 入参校验（v0.74.4 起真正生效）

此前 `SyncPushRequest.sessions` 缺 `@Valid`，集合元素上的约束从未生效（空 projectName/language 被静默存成空串）。v0.74.4 修复并补了长度上界：

| 字段 | 约束 |
|---|---|
| `sessionUuid` | 非空 |
| `projectName` | 非空，≤255 |
| `language` | 非空，≤50 |
| `startTime` / `endTime` / `clientModifiedAt` | 非空 |
| `clientVersion` | ≥ 0 |

- 违规 = **400**（原子批：一个不合规则整批被拒）；此前是"静默通过"
- 400 无匹配 error code → `SyncErrorMapper` 落 `VALIDATION_ERROR`（映射表无 400 专属码；429/5xx 按状态码先行）
- **确定性失败**：重试必复现——不是瞬时错误，不适用"重试即解决"的直觉

插件侧合规证据（2026-09 独立核查）：
- 取值来源：`projectName = project.name`、`language = file.fileType.name`（`TimeTrackerService.kt`）、`sessionUuid` 本地 UUID、时间戳非空类型、`clientVersion` 计数器
- 真实数据只读核查（3557 条本地会话）：空/超长/负值**零违规**（max language 19 字符 / max projectName 19 字符；45 条待推脏会话 0 违规）

已知的潜在契约依赖（当前不可达）：`SyncSessionApplier` 用 `projectName.orEmpty()` 处理服务端快照的 null——apply 的行 `is_synced=1` 不进 push 路径，除非该行本地被改脏（当前无此路径）。

## 插件侧常量

| 常量 | 值 | 位置 |
|---|---|---|
| `pushBatchSize` | 500 | `SyncCoordinator` 构造参数 |
| 定时同步间隔 | 5 分钟（可配，0=关） | `SyncSettingsState.syncIntervalMinutes` |
| HTTP 超时 | 连接/请求见 `SyncHttpClient` | 429 按 Retry-After 退避（header 优先，body ISO-8601 兜底，60s 上限） |

## 错误码映射（`SyncErrorMapper` / `SyncErrorKind`）

| Kind | 触发 |
|---|---|
| `API_KEY_INVALID` / `API_KEY_EXPIRED` / `API_KEY_REVOKED` / `SCOPE_DENIED` | 401 AUTH_010/011、403 AUTH_012、403 AUTH_020 |
| `DEVICE_NOT_FOUND` | 404 COMMON_002（设备不存在/已吊销）→ 自愈重注册 |
| `RATE_LIMITED` | 429（状态码优先于 body code） |
| `NETWORK_ERROR` / `TIMEOUT` | 传输层 |
| `SERVER_ERROR` | 5xx（分页护栏/apply 失败也复用此 kind + message） |

## 本地存储

| 位置 | 说明 |
|---|---|
| `sync_cursor` 表 | `(user_id, device_id)` PK；`last_pulled_change_id`（单调 MAX 守卫）、`last_push_at`、`last_sync_at` |
| `coding_sessions` 同步列 | `is_synced`(0/1)、`synced_at`、`sync_version`、`owner_user_id`、`is_deleted` |
| `app_user` 表 | 跨 IDE 一致的安装级 user id（`UserManager.getOrCreateUserId`） |

## 关键提交（定位历史决策）

| 提交 | 内容 |
|---|---|
| `56a2de9` | feat(sync): pull 分页 + 批量 upsert + 墓碑 lifting（0.20.0） |
| `6893bc7` | fix(stats): mergeIntervals（0.20.1，统计侧但共用 TimeRangeUtils） |
| `8f15b89` | 同步核心（0.17.0） |
| `f36de05` | 同步调度（0.18.0） |
