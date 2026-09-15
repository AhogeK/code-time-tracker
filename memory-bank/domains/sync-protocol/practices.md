# sync-protocol — practices

## 批量写：抄 `upsertSyncedSessions` 的形态

```kotlin
connectionManager.withConnection { conn ->
    conn.autoCommit = false
    conn.prepareStatement(sql).use { pstmt ->
        for (session in sessions) { /* bind 1..12 */ pstmt.addBatch() }
        pstmt.executeBatch()
        conn.commit()
    }
}
// catch: log.error(...); throw e   ← 必须抛出，调用方（协调器）据此不推游标
```

- 页大小 = 服务端 `pull-batch-size`（默认 1000）；`executeBatch` 在单事务内已消除逐行 fsync，**不要**改用多值 INSERT（参数上限 `SQLITE_MAX_VARIABLE_NUMBER`，复杂不值）
- DELETE 路径（`markDeleted`）保持逐条：软删罕见，内联执行即可

## 连续段缓冲（applier）

```kotlin
val pendingUpserts = mutableListOf<CodingSession>()
fun flushUpserts() { if (pendingUpserts.isNotEmpty()) { repository.upsertSyncedSessions(pendingUpserts.toList(), ownerUserId); pendingUpserts.clear() } }
// UPSERT → pendingUpserts += ...（new / clean 覆盖；dirty 跳过）
// DELETE → flushUpserts(); repository.markDeleted(uuid)   ← 先 flush 保序
// 循环结束后 flushUpserts()
```

naive"全页收集后一次写"是错的：`upsert A → delete A` 会变成"先删后写"，终态错误。

## 分页循环（`pullAndApplyPages` 形态）

```kotlin
var page = firstPage; var cursor = initialCursor
while (true) when (page) {
    is Failure -> return page
    is Success -> {
        val data = page.data
        if (data.hasMore && data.nextCursor <= cursor) return Failure(护栏)   // 防永卡
        try { applier.apply(...); cursorRepository.setPullCursor(userId, deviceId, data.nextCursor) }
        catch (e: Exception) { return Failure(异常转失败) }                    // 防穿透
        cursor = data.nextCursor
        if (!data.hasMore) return Success(cursor)
        page = api.pull(SyncPullRequest(deviceId, cursor), apiKey)
    }
}
```

## DTO 兼容：新字段必须给旧端安全默认

```kotlin
data class SyncPullResponse(
    val changes: List<SyncChangeDto> = emptyList(),
    val nextCursor: Long = 0L,
    val hasMore: Boolean = false,   // 旧服务端缺字段 → Gson 走默认 → 单页行为
)
```

Gson 不走构造器、缺字段填默认；新增字段一律带默认值。

## 测试写法（FakeApi + 真实 SQLite）

- `SyncCoordinatorTest`：`FakeApi` 持 `ArrayDeque<SyncResult<SyncPullResponse>>`（`pullResponses.add(...)` 按序弹），真实临时库 `@TempDir` + `MigrationManager.migrate()`
- 种子**必须用 `upsertSyncedSessions`**（clean 行）；`importSessions` 不带 `is_synced` 列 → 种子行默认 dirty → applier 会跳过，断言会静默落空（**踩过的坑**）
- 多页用例：只给首轮队列塞多页 + reconcile 补一页；断言 `pullCalls` 的 `lastPulledChangeId` 序列
- 失败用例：页 2 塞 `Failure(...)`；断言游标停在页 1 的 `nextCursor` 且页 1 已应用
- 红-绿纪律：bug 修复先验证"修复前测试失败"（可 `git stash` 源码单跑），再验修复后全绿

## 本领域踩坑清单

| 坑 | 事实 |
|---|---|
| `importSessions` 不带 `is_synced` | 种子行默认 `is_synced=0`（dirty），applier 会跳过，测试断言莫名落空 |
| Kotlin 增量编译陈旧 | 改 DTO 构造签名后可能出现 `NoSuchMethodError` 假失败——用 `./gradlew clean test` 排除 |
| `sumOf` 不支持 Duration | `Duration` 非 `Int/UInt`，用 `fold(Duration.ZERO) { acc, x -> acc.plus(...) }` |
| edit 工具的行号漂移 | 大段替换后残留旧行会导致语法错误——替换后立刻读回改动区域 |
| 服务端 watermark 乐观推进 | 失败页不会被重发（principles #3），别按"重试即补偿"设计 |
