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

## 分页循环（`pullAndApplyPages` 形态，0.22.0 起）

```kotlin
var cursor = initialCursor
while (true) when (val page = pullPage(deviceId, cursor, apiKey)) {
    is Failure -> return page
    is Success -> {
        val data = page.data
        if (data.hasMore && data.nextCursor <= cursor) return Failure(护栏)   // 防永卡
        try { applier.apply(...); cursorRepository.setPullCursor(userId, deviceId, data.nextCursor) }
        catch (e: Exception) { return Failure("起点 cursor=$cursor / ${data.changes.size} 条：$e") }
        cursor = data.nextCursor
        if (!data.hasMore) return Success(cursor)
    }
}
```

`pullPage` 是唯一取页入口：请求带 `replayFromChangeId = cursor`；400 且消息点名该字段时回退为不带字段的请求，并按服务器 URL 暂停重放 1 小时（其它 400 只回退不暂停）；它同时包住"404 COMMON_002 → 重注册 → 重试同一请求"，所以翻页中途被吊销也能自愈（0.22.0 之前首拉有自愈、翻页与 reconcile 没有，行为不一致）。

## 响应压缩（gzip）

- 每个请求都带 `Accept-Encoding: gzip`（`SyncHttpClient.trySend`）
- **只按响应头判断是否解压**：`Content-Encoding` 含 `gzip` → `GZIPInputStream`，否则原样读取；按请求头判断会把 identity 读坏或二次解压
- 字节读完统一按 UTF-8 解码（JSON 默认编码；服务端不总带 charset 参数）
- 解压失败 → `SyncErrorKind.UNKNOWN` + "Failed to decode the response body (Content-Encoding: gzip)"，**不能**落成 NETWORK_ERROR（会把排查方向带偏）
- 服务端只在 ≥2 KB 且 `application/json|text/plain` 时压缩：稳态小页本来就是 identity，收益集中在首次全量（106 页 / 53 MB）

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
| `sumOf` 不支持 Duration | `Duration` 非 `Int/UInt`，用 `fold(Duration.ZERO) { acc, x -> acc.plus(...) }` |
| edit 工具的行号漂移 | 大段替换后残留旧行会导致语法错误——替换后立刻读回改动区域 |
| 服务端 watermark 乐观推进 | 普通 pull 会跳过失败页；必须带 `replayFromChangeId`（principles #3），别指望"下轮用旧游标自动重发" |
| 构建缓存还原陈旧测试字节码 | 数据类构造签名变更后 `rm -rf build` 不够（缓存会把旧测试类放回来）；用 `--no-build-cache --rerun-tasks`，`compileTestKotlin` 已设为不进缓存 |
| 会话身份字段不参与 `ON CONFLICT` | `ide_name`/`ide_version` 只在 INSERT 写入：服务端快照不得改写会话来源 IDE；同步 SQL 的 SET 列表刻意不含它们 |
