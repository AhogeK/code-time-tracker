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

## Decision-002: 

**日期**: 
**状态**: 

### 背景

### 决策

### 后果

---
