# Implementation Plan

> 最后更新: 2026-09-17
> 职责：阶段计划（大阶段划分与状态）。版本级交付见 `progress.md`；逐条变更见 `activeContext.md`。

## 阶段列表

### Phase 1: DatabaseManager 重构 ✅ 已完成（2026-02-26）

ConnectionManager / MigrationManager / SessionRepository / StatsRepository 拆分 + Facade。详见 `docs/DATABASE-MANAGER-REFACTORING.md`、Decision-003。

### Phase 2: 云同步 A–E ✅ 全部完成（0.9.0 → 0.20.1）

| 阶段 | 内容 | 状态 |
|---|---|---|
| A | 同步基础设施（HTTP 传输 / API Key 生命周期 / 设置界面 / 错误码映射） | ✅ |
| B | 本地模型对齐（DTO/映射）+ 变更追踪（getDirtySessions） | ✅ |
| C | 同步核心（pull/push/游标/编排 + 端到端真实验收） | ✅ |
| D | 调度与集成（定时 5min 兜底 + 生命周期 flush + 设置页 UI） | ✅ |
| E | 端到端验证与文档（收敛测试 + README 排障） | ✅ |
| 后续 | ctt-server 版本跟进：v0.62.0 分页对接、批量 upsert、墓碑 lifting（0.20.0） | ✅ |

### Phase 3: 会话级 IDE 元数据 ✅ 插件侧双向完成（0.21.0 / 0.21.1）

| 改动 | 状态 |
|---|---|
| 本地数据：`CodingSession.ideVersion` + SQLite `ide_version`（幂等迁移）+ 追踪记录 `fullVersion` + 导出/导入携带 | ✅ 0.21.0 |
| push：`SyncSessionDto`/`SyncSessionMapper` 逐会话发送 `ideName/ideVersion` | ✅ 0.21.0 |
| pull：`SyncChangeDto` 可选 IDE + applier 优先采用服务端值、缺失回退本地 | ✅ 0.21.1 |
| 服务端：`SyncSessionDto` 可选字段 + `coding_sessions` IDE 列 + 统计回退 | ⬜ 待 ctt-server 实施（插件先行兼容） |

### Phase 4: 待定

- 等待用户指示；候选方向不预登记

## 技术债务

- [ ] 待清理无用的 import 语句（Phase 1 遗留）

## 领域知识

跨轮次复用的判断已沉淀至 `memory-bank/domains/`（R29）：`sync-protocol`、`stats-aggregation`。新增领域按需建档。
