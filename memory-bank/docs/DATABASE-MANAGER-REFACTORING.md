# DatabaseManager 重构设计

## 概述

### 背景

`DatabaseManager.kt` 当前体积达到 **1446 行 / ~58KB**，是典型的"上帝类"气味：

- 超过 10 个 public 方法处理不同职责
- 连接管理、表创建、会话持久化、统计查询混在一起
- 难以测试、维护和扩展

### 目标

按职责拆分为单一职责类，提升代码可读性、可测试性和可维护性。

### 用户故事

- 作为开发者，我希望数据库相关代码按职责分离，以便快速定位和修改特定功能
- 作为维护者，我希望每个类有清晰的边界和文档，便于理解和测试
- 作为未来功能开发者，我希望新功能可以添加到对应的专用类中，而非修改巨大的上帝类

---

## 技术设计

### 架构拆分

```
DatabaseManager (重构前)
    │
    ├── ConnectionManager      ← 连接管理 + 配置
    ├── MigrationManager      ← 表结构初始化 + 索引
    ├── SessionRepository     ← 会话 CRUD
    └── StatsRepository       ← 统计查询
```

### 新增类设计

#### 1. ConnectionManager

**职责**: 数据库连接生命周期管理

```kotlin
class ConnectionManager {
    // 保留: withConnection, setConnectionFactory, initialize
    // 移除: 所有业务查询方法
}
```

**关键方法**:

- `withConnection<T>(block: (Connection) -> T): T` - 连接获取
- `setConnectionFactory(factory: ConnectionFactory, urlHint: String?)` - 连接工厂设置
- `initialize()` - 显式初始化入口

**依赖**:

- `ConnectionFactory` (已存在)
- `PathUtils.getDatabasePath()`

#### 2. MigrationManager

**职责**: 数据库 schema 版本管理和迁移

```kotlin
class MigrationManager(private val connectionManager: ConnectionManager) {
    // 迁移方法，按版本号管理
    fun migrate()
    private fun createTableIfNotExists()
}
```

**关键方法**:

- `migrate()` - 入口，版本检测 + 顺序执行迁移
- `createTableIfNotExists()` - 从 DatabaseManager 移入

**依赖**:

- `ConnectionManager`

#### 3. SessionRepository

**职责**: 会话的增删改查（CRUD）

```kotlin
class SessionRepository(private val connectionManager: ConnectionManager) {
    fun saveSessions(sessions: List<CodingSession>, onComplete: () -> Unit)
    fun importSessions(sessions: List<CodingSession>): Int
    fun getSessions(startTime: LocalDateTime?, endTime: LocalDateTime?): List<CodingSession>
    fun getAllSessionUuids(): Set<String>
    fun getAllActiveSessionTimes(): List<SessionSummaryDTO>
    fun getRecordCount(): Long
    fun getFirstRecordDate(): LocalDate?
}
```

**关键方法**:

- `saveSessions()` - 批量保存会话
- `importSessions()` - 导入（去重）
- `getSessions()` - 条件查询

**依赖**:

- `ConnectionManager`
- `UserManager`
- `CodingSession` 模型

#### 4. StatsRepository

**职责**: 统计和聚合查询

```kotlin
class StatsRepository(private val connectionManager: ConnectionManager) {
    fun getTotalCodingTime(projectName: String? = null): Duration
    fun getCodingTimeForPeriod(startTime: LocalDateTime, endTime: LocalDateTime, projectName: String? = null): Duration
    fun getDailyCodingTimeForHeatmap(startTime: LocalDateTime, endTime: LocalDateTime): List<DailySummary>
    fun getCodingStreaks(startTime: LocalDateTime, endTime: LocalDateTime): CodingStreaks
    fun getCodingStreaks(): CodingStreaks
    fun getDailyHourDistribution(startTime: LocalDateTime?, endTime: LocalDateTime?): List<HourlyDistribution>
    fun getOverallHourlyDistributionWithTotalDays(
        startTime: LocalDateTime?,
        endTime: LocalDateTime?
    ): HourlyDistributionResult
    fun getLanguageDistribution(startTime: LocalDateTime?, endTime: LocalDateTime?): List<LanguageUsage>
    fun getProjectDistribution(startTime: LocalDateTime?, endTime: LocalDateTime?): List<ProjectUsage>
    fun getTimeOfDayDistribution(startTime: LocalDateTime?, endTime: LocalDateTime?): List<TimeOfDayUsage>
}
```

**关键方法**:

- 所有 `get*` 统计方法从原 DatabaseManager 移入

**依赖**:

- `ConnectionManager`
- `TimeRangeUtils` 工具类
- 各种 DTO 模型 (DailySummary, CodingStreaks, HourlyDistribution 等)

---

### DatabaseManager 演进

**重构后**: Facade 模式，对外提供统一入口，内部委托给各子组件

```kotlin
object DatabaseManager {
    private val connectionManager = ConnectionManager()
    private val migrationManager = MigrationManager(connectionManager)
    private val sessionRepository = SessionRepository(connectionManager)
    private val statsRepository = StatsRepository(connectionManager)

    // 兼容现有调用，内部委托
    fun saveSessions(...) = sessionRepository.saveSessions(...)
    fun getSessions(...) = sessionRepository.getSessions(...)
    fun getTotalCodingTime(...) = statsRepository.getTotalCodingTime(...)
    // ... 其他方法类似

    fun shutdown() {
        connectionManager.shutdown() // 保留连接管理器的关闭
    }
}
```

---

### 数据库兼容性

- SQLite schema **保持不变**
- 不需要数据迁移
- 所有查询 SQL 保持一致

---

## 实现计划

### Phase 1: 基础设施拆分

- [ ] 创建 `ConnectionManager` 类，提取连接管理逻辑
- [ ] 创建 `MigrationManager` 类，提取建表逻辑
- [ ] 更新 `DatabaseManager` 使用新类

### Phase 2: 数据层拆分

- [ ] 创建 `SessionRepository` 类，提取会话 CRUD
- [ ] 将 `saveSessions`, `importSessions`, `getSessions` 等移入
- [ ] 更新 `DatabaseManager` 委托

### Phase 3: 统计层拆分

- [ ] 创建 `StatsRepository` 类，提取所有统计方法
- [ ] 移动 `getDailyCodingTimeForHeatmap`, `getCodingStreaks` 等
- [ ] 更新 `DatabaseManager` 委托

### Phase 4: 清理与测试

- [ ] 删除 DatabaseManager 中的重复代码
- [ ] 添加单元测试覆盖新类
- [ ] 运行现有集成测试确保兼容性
- [ ] 代码审查

---

## 风险与缓解

### 风险 1: 循环依赖

**描述**: ConnectionManager, MigrationManager, SessionRepository, StatsRepository 可能形成循环依赖。

**缓解**:

- ConnectionManager 作为基础，其他类依赖它
- 明确单向依赖关系
- 构造函数注入

### 风险 2: 现有调用方修改

**描述**: 项目中大量调用 `DatabaseManager.xxx()` 方法。

**缓解**:

- DatabaseManager 保留原有 public API，内部委托
- 渐进式迁移，不破坏现有调用

### 风险 3: 测试覆盖

**描述**: 原 DatabaseManager 未充分测试，拆分后需补充。

**缓解**:

- 优先迁移到新类的代码编写测试
- 保留现有集成测试确保行为不变

---

## 验收标准

### 功能验收

- [ ] 所有原有 `DatabaseManager` public 方法保持兼容
- [ ] 数据库 schema 不变，数据完整
- [ ] 现有功能（时间追踪、统计展示）工作正常

### 代码质量

- [ ] DatabaseManager 缩减至 ~200 行
- [ ] 每个新类单一职责清晰
- [ ] 无循环依赖
- [ ] 代码注释完善

### 测试验收

- [ ] 新类有基本单元测试
- [ ] 现有测试全部通过
- [ ] 手动功能验证通过
