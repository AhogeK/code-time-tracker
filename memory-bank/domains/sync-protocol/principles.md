# sync-protocol — principles

## 1. 游标只由 pull 推进

push 响应里的 `nextCursor` 是"服务端当前最大 changeId"，**不作 pull 起点**——否则会跳过本机自己的 change。reconcile pull 从首轮 pull 后的游标开始，把 push 产生的 change（含本机回声）拉回来幂等覆盖。

## 2. 每页 apply → 立即持久化游标

分页循环的顺序恒为：apply 本页 → `setPullCursor(本页 nextCursor)` → 下一页。apply 或持久化失败 → 返回 Failure，**游标停在最后成功页**。本地游标单调不后退（SQL `MAX(last, excluded)`），乱序/重放响应不会回退。

## 3. 失败页不回退——服务端 watermark 是乐观的

服务端在**生成响应时**即推进自己的 watermark（`advancePullWatermark`），客户端下轮用旧游标重试会被 `queryCursor = max(服务端值, 客户端值)` 跳过失败页。因此：

- 应用失败的一页**不会**被重新下发；其数据靠 LWW 自愈（这些会话下次被任何设备修改时产生新 change 重新收敛）
- 这是自 v0.49 单次 pull 就存在的已知缺口，分页把影响面从"整次响应"缩到"一页"
- 彻底解决需服务端改为客户端确认制（at-least-once），当前**接受现状**，不做插件侧绕过

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
