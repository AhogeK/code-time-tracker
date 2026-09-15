# Implementation Plan

> 最后更新: 2026-09-03
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

### Phase 3: 待定

- 等待用户指示（无进行中的阶段计划）
- 候选方向不在此预登记——需求确认后新增

## 技术债务

- [ ] 待清理无用的 import 语句（Phase 1 遗留）

## 领域知识

跨轮次复用的判断已沉淀至 `memory-bank/domains/`（R29）：`sync-protocol`、`stats-aggregation`。新增领域按需建档。
