# stats-aggregation — references

> 事实查表，不含判断。

## StatsRepository 公共 API

| 方法 | 口径要点 |
|---|---|
| `getTotalCodingTime(projectName?)` | 全量并集 |
| `getCodingTimeForPeriod(start, end, projectName?)` | 区间并集 |
| `getDailyCodingTimeForHeatmap(start, end)` | 日维度并集 → `List<DailySummary>` |
| `getCodingStreaks(start, end)` / `getCodingStreaks()` | 活跃日集合（默认近一年） |
| `getDailyHourDistribution(start?, end?)` | 天×小时分布（先按天并集再按小时切片），默认范围取库内 min/max |
| `getOverallHourlyDistributionWithTotalDays(start?, end?)` | 24 小时聚合 + totalDays |
| `getLanguageDistribution(start?, end?)` | 按语言聚合 |
| `getProjectDistribution(start?, end?)` | 按项目聚合 |
| `getTimeOfDayDistribution(start?, end?)` | 按时段（Morning/Daytime/Evening/Night）聚合 |

## SQL 常量与过滤

| 名称 | 内容 |
|---|---|
| `SQL_SELECT_SESSIONS_IN_RANGE` | `SELECT start_time, end_time FROM coding_sessions WHERE is_deleted = 0 AND end_time > ? AND start_time < ?` |
| `SQL_OWNER_FILTER` | `(owner_user_id = ? OR owner_user_id IS NULL)` |
| `ownerCondition()` | owner 非空时拼 ` AND $SQL_OWNER_FILTER` |
| 参数顺序 | 1=start, 2=end, 3=owner（有 owner 过滤时） |

## 8 个图表数据源（`statistics/`）

| Provider | chartKey | 数据来源 |
|---|---|---|
| `SummaryDataProvider` | summary | `getCodingTimeForPeriod` × 5 周期 + 日均 |
| `YearlyActivityDataProvider` | yearlyActivity | `getDailyCodingTimeForHeatmap` + `getCodingStreaks` |
| `RecentActivityDataProvider` | recentActivityChart | 近 14 天日汇总 |
| `DailyHourDataProvider` | hourlyHeatmap | `getDailyHourDistribution` |
| `OverallHourlyDataProvider` | overallHourlyChart | `getOverallHourlyDistributionWithTotalDays` |
| `LanguageDistributionDataProvider` | languageDistributionChart | `getLanguageDistribution` |
| `ProjectDistributionDataProvider` | projectDistributionChart | `getProjectDistribution` |
| `TimeOfDayDistributionDataProvider` | timeOfDayDistributionChart | `getTimeOfDayDistribution` |

## Yearly heatmap 时长层级（`main.js` `renderYearlyActivityHeatmap`）

| 区间（秒） | 标签 | 色值 |
|---|---|---|
| `[1, 900)` | `< 15 min` | `#00441b` |
| `[900, 3600)` | `15–60 min` | `#006d32` |
| `[3600, 7200)` | `1–2 h` | `#238b45` |
| `[7200, 18000)` | `2–5 h` | `#41ab5d` |
| `[18000, 28800)` | `5–8 h` | `#74c476` |
| `[28800, ∞)` | `> 8 h` | `#bae4b3` |

（`gte`/`lt` 半开区间键；`visualMap` 的 `min`/`max` 顶层字段已删除——pieces 显式给出时被忽略）

## 关键文件与提交

| 位置/提交 | 内容 |
|---|---|
| `util/TimeRangeUtils.kt` | `mergeIntervals` / `calculateMergedDuration` / `getWeekStart` 等（ISO 周一为首日） |
| `6893bc7` | 0.20.1：`mergeIntervals` 抽取 + weekly-hour 修复 |
| `d4c5c21`/`0378270` | 0.19.7：heatmap 日维度重叠合并 |
| `ce39d17` | 0.19.8：heatmap 层级重设（gte/lt） |
| Decision-004（`decisions.md`） | StatusBar 与统计口径统一 |
