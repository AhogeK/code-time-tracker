# stats-aggregation — principles

## 1. 重叠一律并集，且只有一个实现

并行 IDE 窗口（如 10:00–11:00 与 10:30–10:45）是真实场景，任何"逐条累加"都会双计。规则：

- 唯一实现 `TimeRangeUtils.mergeIntervals(intervals) -> List<Pair<LocalDateTime, LocalDateTime>>`（返回并不相交区间列表，按 start 排序）
- `calculateMergedDuration` = `mergeIntervals(...).fold(ZERO) { … }`——**不允许**任何调用方自行实现合并
- 需要切片的下游（heatmap 按天、weekly-hour 按小时）必须先合并**再**切片：`收集区间 → mergeIntervals → 切片累加`
- 先例：heatmap 日维度（0.19.7 修）、weekly-hour（0.20.1 修）都因漏合并而双计

## 2. 统计范围恒为：未删除 + owner 过滤

- 所有查询 `is_deleted = 0`
- `ownerUserId` 非空时追加 `(owner_user_id = ? OR owner_user_id IS NULL)`：**未归属本地会话计入当前账号**（它们将在下次 push 归属过去，立即计入才不滞后）
- 未绑定（owner 为空）：不做 owner 过滤（全量）
- 导出（`getSessions`）**不**过 owner 过滤——导出是本地数据保全，不是统计

## 3. 时间边界：区间相交用半开语义

- 范围查询：`end_time > rangeStart AND start_time < rangeEnd`（重叠即命中，不是包含）
- effective range 裁剪后再统计，跨天会话按天切开分别计入
- 统计时长 = `end - start` 的并集，不使用 `uiDisplayTime` 等运行时累积值（StatusBar 已统一为「数据库查询」口径）

## 4. UI 口径必须与统计口径一致

- StatusBar（Today/Week/Month/Year）与统计页同源：都查数据库 + 同套并集语义；历史上二者不一致的根因就是 Widget 端加了"实时累积"（Decision-004）
- 实时性通过查询频率解决，不通过另一套计算路径解决

## 5. 图表层级是口径的一部分

- Yearly heatmap 时长层级（`visualMap.pieces`）用 **`gte`/`lt` 半开区间**：`[900, 3600)` = "15–60 min"，整边界值（恰好 15 分钟）落**上层**
- 不回归旧式 `min`/`max` 键：echarts 对二者按**双闭区间**处理且首命中生效，边界值会错层（300s 曾误落 `< 5 min`）
- 判定单日数值的是**当日并集总时长**（不是会话简单相加），与 principles #1 同源

## 6. weekday 平均的分母是"窗口内该 weekday 出现次数"

weekly-hour 图：累加值 ÷ `calculateWeekdayCount`（窗口内该周几的日历天数），不是 ÷ 活跃天数。窗口边界日不足整天仍计一次——这是**有意**的口径，改动需用户确认。
