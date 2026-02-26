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
