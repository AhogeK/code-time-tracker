# sync-protocol — principles

## 1. 游标只由 pull 推进

push 响应里的 `nextCursor` 是"服务端当前最大 changeId"，**不作 pull 起点**——否则会跳过本机自己的 change。reconcile pull 从首轮 pull 后的游标开始，把 push 产生的 change（含本机回声）拉回来幂等覆盖。

## 2. 每页 apply → 立即持久化游标

分页循环的顺序恒为：apply 本页 → `setPullCursor(本页 nextCursor)` → 下一页。apply 或持久化失败 → 返回 Failure，**游标停在最后成功页**。本地游标单调不后退（SQL `MAX(last, excluded)`），乱序/重放响应不会回退。

## 3. 失败页靠 replay 重读，不靠 watermark 回退

服务端在**生成响应时**即推进自己的 watermark（`advancePullWatermark`），正常 pull 用 `queryCursor = max(服务端值, 客户端值)` 会跳过客户端没应用到的那一页——那是数据丢失，不是延迟。

因此**每次 pull 都带 `replayFromChangeId = 本地游标`**（`SyncCoordinator.pullPage`）：服务端取 `min(replay, watermark)` 作为读取起点，`nextCursor` 仍以 watermark 为下界。正常态两者相等、行为不变；失败一页时下轮从该页起点重读。

- 本地游标即"已成功应用"的真相；进程被杀也不丢（游标已持久化），无需额外重试计数器
- 旧服务端不识别该字段 → 400 → 同轮回退为不带字段的请求；回退成功才按服务器 URL 记住（换地址重新探测，重启再探测一次）
- 本地游标 > watermark（服务端游标行丢失/重置）时，replay 从 watermark 重读：幂等，最坏一次全量
- `nextCursor = max(页尾, watermark)` 保证重放不能让设备游标倒退；若服务端将来清理 change 行而 watermark 偏高，仍可能跳过（当前服务端无清理任务）
- 同一页反复重放（坏数据）不会无限循环：每轮失败即返回，节奏由调度器控制，错误在设置页可见；不做跨轮计数器（要持久化就得改表，收益不足）

## 4. 客户端 apply 必须幂等

同一页可能因重试/发送旧游标被重复应用：

- UPSERT → `INSERT … ON CONFLICT DO UPDATE`（可重复执行）
- DELETE → `UPDATE … WHERE is_deleted = 0`（重复即 no-op）
- 重建行不会复活旧状态（conflict 分支覆盖全字段）

## 5. dirty 行优先，服务端快照对 clean 行权威

- pull 应用 UPSERT：本地 `is_synced = 0` 的行**不覆盖**（本地未推送修改优先，交由 push 后服务端裁决）
- clean 行被覆盖；本地缺失的行新建
- DELETE：clean 行软删；dirty 行保留（本地编辑赢到下次 push）

## 6. 服务器 upsert 是权威活状态（墓碑 lifting）

`ON CONFLICT DO UPDATE` 包含 `is_deleted = 0`。同一页 `upsert → delete → upsert`（或跨轮：设备 A 删除、设备 B 又修改）时，终态必须是**活行**——服务器对它主动 upsert 的快照即权威。旧行为（不 lifting）会让墓碑吞掉活状态、行在本地永久不可见。

## 7. 分页有数学终点，但护栏必须有

服务端非空页 `nextCursor = max(页尾 changeId, 客户端游标)` 严格递增，空页必 `hasMore=false` → 循环可终止。但若服务端异常返回 `hasMore=true` 且游标不前进，循环会永久占住 `syncInProgress`（后续所有 sync 静默 no-op）。**护栏**：`hasMore && nextCursor <= cursor` → 立即 Failure。

## 8. push 原子且分批

- 一批（≤500 会话）在服务端单事务落地：整批成或整批不成，客户端才可标记 synced
- 批次失败 → 该批保留 dirty，下轮重推（幂等）
- 大批量本地历史必须分批，不允许单请求超时预算

## 9. 设备自愈：404 COMMON_002 → 重注册 → 重试一次

设备被吊销（服务端保留行、停同步）时，用同一 key 重发注册即可清除 revoked_at 恢复同步。触发点：首轮 pull 与每次 push，**每轮最多一次**。

## 10. 账号隔离（owner scope）

- 绑定用户下的统计/推送按 `owner_user_id` 隔离；未归属本地会话（`owner_user_id IS NULL`）计入当前账号
- 换绑（A→B）：清本地游标 + 旧会话 `markAllSynced`（不推给新账号）+ 清 serverUserId/statsOwner
- 绑定成功后 `GET /users/me` 解析 serverUserId 并设 stats owner

## 11. 异常不得穿透同步轮次

apply/游标写异常必须转为 `SyncResult.Failure`（含原始消息供设置页显示）。异常逃逸会杀死后台线程的整轮，`lastSyncError` 不更新、状态不可见。
