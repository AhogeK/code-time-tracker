# sync-protocol — scenarios

## 服务端发布新版本，要对接插件侧

1. 读 `../ctt-server` 对应源码（只读）核对契约：端点、DTO 字段、默认值、边界语义（R3）
2. 判断兼容性：新字段必须能给旧插件安全默认（如 `hasMore: Boolean = false`——缺字段 = 单页）
3. 检查失败语义变化：新错误码 → `SyncErrorMapper` 映射；新 4xx/5xx → 用户提示
4. 实现 + 测试（新 DTO 默认值用例、循环/边界用例）
5. 更新本领域 `references.md`（契约表）与 `meta.md` 核对基线

## 同步失败排查（设置页显示错误 / 数据未收敛）

| 症状 | 检查 |
|---|---|
| `Cannot reach the server` | 地址、网络、`SyncHttpClient` 超时；429 退避是否生效 |
| API key 类错误 | `SyncErrorKind` 映射是否正确（401/403 分支） |
| 设备 404 | 是否触发自愈（重注册+重试）；key 与设备绑定关系 |
| 拉取停在半路 | 游标值 vs 服务端 watermark（缺口=失败页被跳过，见 principles #3）；`lastSyncError` 消息 |
| 数据来回翻转 | LWW 链（delete→serverVersion→clientVersion→clientModifiedAt）；两边 clientVersion 是否递增 |
| 换绑后统计串号 | owner 过滤是否生效（`owner_user_id = ? OR IS NULL`）；换绑 reset 是否执行 |

## 修改分页循环 / 游标逻辑

- 保持顺序：apply 本页 → `setPullCursor` → 下一页
- 保留卡死护栏（`hasMore && nextCursor <= cursor` → Failure）
- 保留异常 → Failure 转换（不得穿透）
- 两处 pull（首轮 + reconcile）都走 `pullAndApplyPages`——不允许只改一处
- 测试：多页推进、中途失败停成功页、缺 hasMore 单页、护栏触发

## 改 SessionRepository 同步写路径

- 批量 upsert 单事务（`autoCommit=false` + `addBatch` + `executeBatch` + 单 `commit`），失败**抛出**（游标才不会前进）
- `ON CONFLICT` 字段表：更新业务字段 + `is_synced=1` + `synced_at` + `sync_version` + `owner_user_id` + `is_deleted=0`；**不动** `session_uuid`/`user_id`/`platform`/`ide_name`（保持原行身份）
- 逐条 API 已被批量版替换（clean cutover），新调用方一律用 `upsertSyncedSessions`

## applier 增删操作顺序

- upsert 缓冲为**连续段**批量写；DELETE 前必须先 flush（同页 `upsert A → delete A` 的顺序语义）
- 同 uuid 多条 upsert：缓冲内按序执行，后者覆盖前者（与逐条语义一致）
- 变更无 sessionUuid → 跳过（契约字段缺失的降级）

## 一次同步轮次的完整时序（修改 `doSyncOnce` 前必读）

```
读取本地游标
 ├─ 首轮 pull（404 → 重注册 → 重试一次）→ pullAndApplyPages（分页直到 hasMore=false）
 ├─ getDirtySessions → 分批 push（每批 500）
 │    └─ 每批：404 → 重注册重试；成功 → markSynced；失败 → 记录首个错误
 │         └─ 任一失败 → 返回 Failure（dirty 保留）
 ├─ reconcile pull（从首轮最终游标）→ pullAndApplyPages
 └─ setLastSyncAt → Success（UI 刷新经 messageBus）
```

## 换绑 / 解绑账号

- 换绑（serverUserId 变化）：`resetForUserSwitch()` —— 清游标、旧会话 markAllSynced、清 serverUserId/statsOwner
- 绑定：注册设备成功 → 解析 `/users/me` → 设 statsOwner → 触发初始同步
- 解绑：清除 key 与设备状态；本地数据保留（不删）
