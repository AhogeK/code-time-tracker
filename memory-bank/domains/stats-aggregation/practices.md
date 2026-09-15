# stats-aggregation — practices

## 收集 → 合并 → 切片（唯一正确的聚合形态）

```kotlin
// 单日维度（heatmap）：按天收集区间 → 每天合并 → 出时长
val dailyIntervals = mutableMapOf<LocalDate, MutableList<Pair<LocalDateTime, LocalDateTime>>>()
// … splitSessionByDay(effectiveStart, effectiveEnd).forEach { (date, interval) -> … }
dailyIntervals.map { (date, intervals) -> DailySummary(date, TimeRangeUtils.calculateMergedDuration(intervals)) }

// 天×小时维度（weekly-hour）：按天收集 → 每天合并 → 按小时切片累加
dailyIntervals.forEach { (_, intervals) ->
    TimeRangeUtils.mergeIntervals(intervals).forEach { (start, end) ->
        splitSessionByDayAndHour(start, end).forEach { (weekday, hour, duration) ->
            distributionMap[Pair(weekday, hour)] = distributionMap.getOrDefault(key, 0L) + duration.toSeconds()
        }
    }
}
```

**反例**（曾出 bug 的形态）：`for (session in sessions) splitByHour(session).sum()` —— 无合并，并行窗口双计。

## `mergeIntervals` 契约

- 输入 `List<Pair<LocalDateTime, LocalDateTime>>`（顺序无关）
- 输出：不相交区间，按 start 升序；空入参 → 空列表
- 相邻不重叠（`nextStart == currentEnd`）**不合并**为一段，但时长等价（无需担心）
- `calculateMergedDuration` 基于它求和（`fold`，不用 `sumOf`——Duration 非 Int/UInt，会有重载歧义）

## 测试数据构造

- 用 `syncedSession(uuid, start, end)` 辅助 + `sessionRepository.upsertSyncedSessions(listOf(...))` 种子（clean 行）
- **不要**用 `importSessions` 做种子：它不写 `is_synced` 列 → 行默认 dirty，统计虽不筛 dirty，但语义上易与其他测试混淆（sync 侧会跳过）
- 重叠用例模板：

```kotlin
// 10:00–11:00 与 10:30–10:45（并行窗口）→ hour 10 并集 = 3600s，不是 3900s
val distribution = statsRepository.getDailyHourDistribution(dayStart, dayEnd)
val hour10 = distribution.first { it.dayOfWeek == 4 && it.hourOfDay == 10 }
assertThat(hour10.totalDuration.toSeconds()).isEqualTo(3600)
```

- 断言用**具体秒数**（`3600`），不用 "大于 0" / "非空"——口径类 bug 只有具体数值能抓住

## webview 图表改动验证（无头 SSR）

改 `visualMap.pieces` / tooltip 数值格式后，用打包的 `echarts.min.js` 做 SSR 探针验证边界落层（见 scenarios 的代码骨架），随后 **runIde 人工验收**一次真实渲染；不要只依赖 SSR（它不覆盖 DOM/主题/布局）。

## 本领域踩坑清单

| 坑 | 事实 |
|---|---|
| echarts `min`/`max` 是双闭区间且首命中 | 相邻档位整边界值落**下层**——必须用 `gte`/`lt` |
| `min`-only 档位（如 `{min: 28800}`）在 echarts 中为左开 | 恰界值不落入该档；配 `gte` 才闭 |
| SSR 探针需匹配颜色出现 | 渲染 SVG 里搜档位色值（hex 或 `rgb()` 形式），`visualMap.show:false` 避免图例色干扰 |
| StatusBar 与统计页两套算法 | Decision-004 就是此类：统一为数据库查询 |
| weekday 覆盖次数分母 | `calculateWeekdayCount` 是窗口内日历天数，不是活跃天数 |
