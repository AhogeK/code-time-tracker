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

## 语言归一化（`util/LanguageVocabulary.kt`）

| 项 | 事实 |
|---|---|
| 词表资源 | `src/main/resources/language/vocabulary.json`（verbatim 副本，来自 ctt-server 同名文件）。**v2**（2026-09-17）：size 39845 bytes，sha256 `be7de60211d7604b68a70bcb399b8252f5d4a51576a88100f995fb9a43a8ea04`，842 canonical / 489 aliases / 76 nonLanguages。v1（92/75/76）是从"单机可枚举的 fileType"反推的，缺 750 种真实语言（Elixir/Zig/Astro/Svelte…）；v2 以标准本身（GitHub Linguist 全集 + 7 个本地扩展）为源，canonical +750 / aliases +414 且**零移除** |
| 服务端实现（语义权威） | `../ctt-server` `language/LanguageVocabulary.java`：`key() = strip + Locale.ROOT lowercase`；索引以 canonical 优先 `putIfAbsent`，alias 指向未知名在服务端**快速失败**、插件侧 warn 忽略 |
| 判定链 | blank→`""`；nonLanguages→`"Other"`；索引命中→规范名；未命中→原样（不 fold） |
| 容错 | 资源加载失败 → 空词表 + error 日志（退化为不归一化，统计不崩）；词表完整性由 `LanguageVocabularyTest` 在 CI 兜底（`version == 2` 版本守卫 + **悬空别名校验**：遍历 aliases 断言目标存在于 canonical，镜像服务端的构造期校验） |
| 未识别值记录 | 插件侧**不做**本地记录（未镜像服务端 `recordUnmapped`）。前提修正（服务端 2026-09 核实）：服务端归一化在**读路径**，未识别值仅在用户请求过服务端统计时才被记录；插件侧本地记录目前只有日志可见性、无送达通道，端到端价值低。触发条件：若建立"未识别值收集/上报"流程，本地记录是前置件 |
| 更新流程 | 服务端词表 `version` 变化 → 复制新文件 → `shasum -a 256` 与公告值核对（不靠肉眼比 JSON）→ 跑 `LanguageVocabularyTest` + `StatsRepositoryTest`。**v1→v2 已按此流程执行**（2026-09-17：哈希/大小/条目数三项与公告一致；独立复核 +750 零移除、后端点名 15 种语言 v1 全缺 v2 全有）。服务端已固化公告规则（ctt-server 领域知识库 "Language vocabulary contract"：任何 vocabulary.json 改动都 bump version 并公告旧→新版本与新增内容）。测试守卫：`version == 2` 断言 + v1 缺失语言的可解析断言（捕获复制错版本） |
| 核实记录 | 2026-09 服务端独立复算通过：sha256 一致、条目数一致、插件测试断言复算一致、判定链逐条等价 |

## 关键文件与提交

| 位置/提交 | 内容 |
|---|---|
| `util/TimeRangeUtils.kt` | `mergeIntervals` / `calculateMergedDuration` / `getWeekStart` 等（ISO 周一为首日） |
| `6893bc7` | 0.20.1：`mergeIntervals` 抽取 + weekly-hour 修复 |
| `d4c5c21`/`0378270` | 0.19.7：heatmap 日维度重叠合并 |
| `ce39d17` | 0.19.8：heatmap 层级重设（gte/lt） |
| Decision-004（`decisions.md`） | StatusBar 与统计口径统一 |
