# Decisions

> 记录关键技术与业务决策

---

## Decision-001: TimeTrackerService.onActivity() 竞态条件修复

**日期**: 2026-02-19
**状态**: 已实施

### 背景

在 `onActivity()` 方法中，`lastActivityTime` 先被 `set(now)` 更新，随后又通过 `get()` 获取用于计算 `timeDelta`。在这两次操作之间，另一个线程可能已经更新了 `lastActivityTime`，导致 `timeDelta` 计算为 0 或负值（虽然有 `> 0` 守护判断，但逻辑上存在瑕疵）。

### 决策

使用 `AtomicReference.getAndSet(now)` 替代分离的 `set()` + `get()` 操作，在更新前原子性地获取旧值，确保 `timeDelta` 计算基于本次更新前的时间点。

### 后果

消除了竞态条件，确保时间增量计算的准确性。修复是向后兼容的，不改变外部行为。

---

## Decision-002: DatabaseManager 线程安全性确认

**日期**: 2026-02-19
**状态**: 已实施（确认安全，仅小修复）

### 背景

用户提出需确认 `DatabaseManager` 的写入操作是否真正线程安全。`pauseAndPersistSessions` 既在 EDT 上调用，也可能在调度线程上调用（`stopTracking()`）。

### 分析结果

**现有实现已具备线程安全性**：

1. **写操作（`saveSessions`）**：
   - 使用 `databaseExecutor`（单线程执行器）串行化所有写入 ✅
   - 回调通过 `invokeLater` 返回 EDT ✅

2. **读操作**（所有 `get*` 方法）：
   - 每次调用通过 `DriverManager.getConnection()` 创建新连接
   - 无共享连接状态 ✅
   - SQLite 支持并发读取 ✅

3. **配置访问**：
   - `config` 字段使用 `@Volatile` 确保跨线程可见性 ✅
   - `withConnection` 使用局部变量副本 ✅

### 发现的问题

当 `sessions` 为空时，`onComplete()` 回调被**直接调用**而非通过 `invokeLater`，这在非 EDT 线程调用时可能导致问题。

### 修复

在空 session 情况下也使用 `invokeLater` 调用回调，确保线程安全。

### 后果

统一回调调用方式，进一步增强线程安全性。不影响外部行为。

---

## Decision-004: StatusBar 时间显示逻辑修复

**日期**: 2026-03-17
**状态**: 已实施

### 背景

用户报告 StatusBar 上的 Today/This Week/This Month/This Year 时间显示与统计页不一致，只有 Total 是正确的。

### 问题分析

`Widget.getTimeForPeriod()` 方法存在逻辑缺陷：

```kotlin
// 错误逻辑：当 serviceTime > 0 时只返回实时累积时间
val serviceTime = timeTrackerService.getUIDisplayTime(modelPeriod)
if (serviceTime > 0) {
    return Duration.ofMillis(serviceTime)  // 忽略了数据库历史！
}
return dbQueryFallback(startOfToday)  // 只有 serviceTime == 0 才查询数据库
```

这导致：

- 有活跃会话时只显示实时累积时间，忽略数据库中已持久化的历史时间
- 数据库中已存在的历史编码时间被完全忽略

### 决策

修复为正确的组合逻辑：数据库历史时间 + 实时累积时间

```kotlin
// 正确逻辑：总是查询数据库，然后加上实时累积
val dbTime = dbQueryFallback(startOfToday)
val serviceTime = timeTrackerService.getUIDisplayTime(modelPeriod)
return dbTime.plus(Duration.ofMillis(serviceTime))
```

### 后果

- StatusBar 与统计页时间显示现在一致
- 用户可以看到完整的编码时间（历史 + 当前会话）
- 不影响现有功能和性能

**补充修复**：

问题进一步分析发现：`session.endTime - session.startTime`（时间区间）与 `uiDisplayTime`（实际编码累积时间）不同步，导致持久化后显示值回退。

**根因**：

- `endTime` 持续更新为每次活动的 `now`（包含空闲间隔）
- `uiDisplayTime` 只在 `timeDelta < IDLE_THRESHOLD_SECONDS * 1000` 时累积
- 两者不同步！

**最终解决方案**：

1. **正确的 `endTime` 计算**：用 `totalSessionTime` 计算实际编码时长，`endTime = startTime + totalSessionTime`
2. **简化 Widget 查询**：直接查询数据库，不再加 `uiDisplayTime`
3. **持久化后立即重置**：`totalSessionTime` 和 `uiDisplayTime` 清零，下次编码从零累积

**效果**：

- StatusBar 显示 = 数据库查询（与统计页一致）
- 响应快速（无异步累积延迟）
- 回退最多 1 秒（数据库查询延迟，可接受）

**并发安全**：

- 持久化前立即重置累积器，确保新会话从 0 开始
- 快照机制确保持久化数据独立于新累积

---

## Decision-003: DatabaseManager 重构为 Facade 模式

**日期**: 2026-02-26
**状态**: 已实施

### 背景

`DatabaseManager.kt` 体积达到 1446 行 (~58KB)，是典型的"上帝类"气味，混合了连接管理、表创建、会话 CRUD、统计查询等多种职责。

### 决策

按职责拆分为 4 个单一职责类：
- `ConnectionManager` - 连接生命周期管理
- `MigrationManager` - 数据库 schema 初始化
- `SessionRepository` - 会话 CRUD 操作
- `StatsRepository` - 统计查询

`DatabaseManager` 保留为 Facade，对外提供统一入口，内部委托给各子组件。

### 关键设计决策

1. **死代码清理**：
   - 移除未使用的 `DatabaseManager.setConnectionFactory()` (仅测试使用)
   - 移除未使用的 `ConnectionSupplier.asFactory()` extension function

2. **向后兼容**：
   - 所有原有 public 方法签名保持不变
   - 内部委托实现

### 后果

- 代码行数减少 91% (1446 → 129)
- 新增 31 个单元测试
- 架构更清晰，便于维护和测试
