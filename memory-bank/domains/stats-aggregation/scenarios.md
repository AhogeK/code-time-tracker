# stats-aggregation — scenarios

## 用户报告"某图表数字偏大 / 比预期多"

1. 先判断是否**重叠双计**：该路径是否在累加前调用了 `mergeIntervals`？
   - 逐条累加 + 切片 = 症状（并行窗口小时的数值 > 该小时存在时长）
2. 核对口径分母：weekday 平均的分母是天数还是次数；owner 过滤是否生效
3. 写**红-绿测试**复现：两个重叠会话（如 10:00–11:00 + 10:30–10:45）断言并集值
   - `git stash` 源码单跑确认"修复前失败"，再恢复全绿——不允许跳过红验证

## 新增一个统计维度 / 图表

1. 判定语义：该维度是否需要并集？（是 → `mergeIntervals` 后再聚合；否 → 说明理由）
2. owner 过滤与 `is_deleted` 必须与既有查询一致（从 `SQL_SELECT_SESSIONS_IN_RANGE + ownerCondition()` 出发）
3. 数据源放 `statistics/<X>DataProvider.kt`，实现 `ChartDataProvider`（`prepareData` + `getChartKey`），并在 `StatisticsView` 的 provider 列表注册
4. webview 侧在 `main.js` 增加 render 分支 + `index.html` 容器 + 卸载/重渲染路径
5. 测试：`StatsRepositoryTest` 加数值口径用例；UI 层改动走 runIde 验收

## 调整 heatmap 层级 / 配色

- `visualMap.pieces` 用 `gte`/`lt`（半开），标签与数值区间一致；改完对照 principles #5 核边界
- 边界值可以用 SSR 无头验证，不必依赖真实数据撞点：

  ```js
  // Bun/Node：require 打包的 echarts.min.js（UMD），SSR 渲染单点，检查命中颜色
  const chart = echarts.init(null, null, {renderer: 'svg', ssr: true, width: 900, height: 260});
  chart.setOption({visualMap: {show: false, type: 'piecewise', pieces}, calendar: {...}, series: {...}});
  const svg = chart.renderToSVGString(); // 断言只出现预期档位颜色
  ```

## 改 `StatsRepository` 的某条 `get*`

- 先读它的 SQL：`is_deleted` / owner 条件 / 时间条件是否齐全
- 找合并点：是否需要 `mergeIntervals`；跨天/跨小时是否先 `splitSessionBy*` 再处理
- 保持既有惯例：`connectionManager.withConnection` + `PreparedStatement`（参数顺序：start, end, owner），异常 `log.error`（统计查询不抛出，返回空/零）
- 跑 `StatsRepositoryTest` + 相关 provider 测试

## 排查"统计与 StatusBar 不一致"

- 二者响应路径：StatusBar → `CodeTimeTrackerWidget` → 数据库查询；统计页 → `StatisticsView` → provider → 同一 repo
- 不一致的常见根因：一边引入了第二套计算（实时累积/不同时间范围工具）——统一回 `TimeRangeUtils` + 数据库查询（Decision-004 先例）
