# stats-aggregation — meta

## Boundary

本地会话数据的统计口径与聚合：从 `coding_sessions` 到各图表的数值语义（时长如何合并、时间如何切片、账号如何隔离），以及这些口径在 UI 层的表达（图表层级、tooltip）。

**In scope**：时长并集语义、日/小时切片、owner 隔离与未归属会话、时间范围边界、语言名归一化（展示层）、8 个数据 provider 的数值口径、图表层级（heatmap 配色梯度）。

**Out of scope**：会话如何产生（追踪引擎）、同步如何写入（`sync-protocol`）、webview 的渲染框架/主题。

## Owned paths

- `src/main/kotlin/com/ahogek/codetimetracker/database/StatsRepository.kt` — 全部统计查询
- `src/main/kotlin/com/ahogek/codetimetracker/util/TimeRangeUtils.kt` — `mergeIntervals` / `calculateMergedDuration` / 时间范围工具
- `src/main/kotlin/com/ahogek/codetimetracker/util/LanguageVocabulary.kt` — 语言名归一化（词表见 references）
- `src/main/resources/language/vocabulary.json` — 词表副本（来源 ctt-server，服务端权威）
- `src/main/kotlin/com/ahogek/codetimetracker/statistics/*DataProvider.kt` — 8 个图表数据源
- `src/main/resources/webview/main.js` — 图表层级（`visualMap.pieces` 等）
- `src/test/kotlin/.../database/StatsRepositoryTest.kt`、`.../util/LanguageVocabularyTest.kt`、`.../statistics/SummaryDataProviderTest.kt`

## Where to start

1. `util/TimeRangeUtils.kt` — `mergeIntervals`（唯一的并集实现，先理解它）
2. `database/StatsRepository.kt` — 各 `get*` 的 SQL 与合并点
3. `statistics/SummaryDataProvider.kt` — 周期汇总（today/week/month/year/total）
4. `statistics/YearlyActivityDataProvider.kt` / `DailyHourDataProvider.kt` — heatmap 两条路径
5. `webview/main.js` — `renderYearlyActivityHeatmap` 的 `visualMap.pieces`

## Terminology

| 术语 | 含义 |
|---|---|
| **并集合并（union）** | 重叠区间取并集后计时长；并行 IDE 窗口不双计。唯一实现 `TimeRangeUtils.mergeIntervals` |
| **effective range** | 会话与查询窗口的交集（`max(start, rangeStart)` … `min(end, rangeEnd)`） |
| **切片** | 把区间按天/小时切开分别累加（`splitSessionByDay` / `splitSessionByDayAndHour`） |
| **owner scope** | `owner_user_id = ? OR owner_user_id IS NULL`——当前账号的已归属 + 未归属本地会话 |
| **weekday 分布** | weekly-hour 图：按 ISO 周几（1=Mon）与小时聚合，再除以该 weekday 出现次数 |

## Verification baseline

| | |
|---|---|
| 核对 | 2026-09-17 · v0.20.2（语言归一化接入本领域时同步核对） |
| 覆盖 | StatsRepository 全部统计方法 + TimeRangeUtils + LanguageVocabulary + 8 个 provider + heatmap 图层；测试 129/129 |
| 已知漂移 | 逐条未复核——改动任一 `get*` 前先读其 SQL 与合并调用 |

未核验部分当作线索，不当作事实。
