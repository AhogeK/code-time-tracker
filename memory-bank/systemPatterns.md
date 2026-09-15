# System Patterns

> 架构模式与设计决策（**横切规范**）。领域专属判断见 `memory-bank/domains/`（R29），两者不重复。

## 时间追踪引擎（TimeTrackerService）

- `@Service(Service.Level.APP)` 应用级单例服务
- 监听编辑器键鼠事件更新 `lastActivityTime`
- 每 5 秒轮询检测空闲状态，超过 60 秒自动持久化 session
- `ConcurrentHashMap` 支持多项目并发追踪

## 数据持久化层（DatabaseManager Facade）

- `ConnectionManager` - 连接管理（`databaseExecutor` 单线程串行化写入）
- `MigrationManager` - 表结构初始化与迁移（schema 变更必须走迁移脚本，见 AGENTS.md R15）
- `SessionRepository` - 会话 CRUD
- `StatsRepository` - 统计查询
- 数据模型：`CodingSession` / `Stats` / `TimePeriod`

## 云同步

- 已实现（A–E 阶段，0.20.1）：LWW 冲突裁决 + 游标 + 服务端分页 + 批量 upsert
- 协议事实、插件侧契约、失败语义见 **`memory-bank/domains/sync-protocol/`**（不在此处重复）
- 涉及同步/API 契约时读取 `../ctt-server` 源码验证（AGENTS.md R3）

## 统计口径（横切约束）

- 重叠会话一律按**并集**合并（`TimeRangeUtils.mergeIntervals`）：summary / heatmap / weekly-hour 三处共用同一实现，禁止任何一处退化为简单累加
- 详细口径与踩坑见 **`memory-bank/domains/stats-aggregation/`**

## 设计决策索引

- `decisions.md`：Decision-001（onActivity() 竞态条件修复）等

## 代码规范速查

- 详见 AGENTS.md R11（语言/注释/Kotlin 风格/Swing EDT/命名/测试/编辑前验证）
