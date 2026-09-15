# Tech Context

> 技术栈与项目结构

## 技术栈

| 维度 | 选型 |
|---|---|
| **开发语言** | Kotlin (JVM 25) |
| **构建工具** | Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin |
| **目标平台** | JetBrains IDE 2026.1+ (Build 261+) |
| **本地存储** | SQLite（via `sqlite-jdbc`） |
| **数据序列化** | Gson（DTO 缺字段走默认值——新字段必须带默认，见 `domains/sync-protocol/practices.md`） |
| **日期选择器 UI** | LGoodDatePicker |
| **测试框架** | JUnit 5 + AssertJ |
| **静态分析** | Qodana |

## 包结构

```
codetimetracker/
├── action/        # IDE Action（菜单/工具栏操作入口）
├── activity/      # 用户活动事件订阅
├── database/      # SQLite 数据持久化层（Repository 模式 + ConnectionManager/MigrationManager）
├── handler/       # 事件处理器
├── listeners/     # IDE 生命周期监听器
├── model/         # 数据模型（CodingSession / Stats / TimePeriod）
├── service/       # 核心业务逻辑层
│   └── sync/      # 云同步（Coordinator/Applier/DTO/HTTP/调度，领域见 domains/sync-protocol/）
├── statistics/    # 统计聚合与 8 个图表数据源（领域见 domains/stats-aggregation/）
├── toolwindow/    # 工具窗口注册
├── topics/        # 消息总线 Topic 定义
├── ui/            # 自定义 Swing 组件
├── user/          # 用户标识管理（跨 IDE 一致 id）
├── util/          # 工具类（TimeRangeUtils 等）
└── widget/        # 状态栏 Widget
```

## 版本信息

- `pluginVersion`：0.20.1（`gradle/libs.versions.toml` 单一来源，`build.gradle.kts` 引用，禁止硬编码）
- 版本规则见 AGENTS.md R18

## 依赖变更记录

- sqlite-jdbc：3.53.2.1 → 3.53.4.0（0.19.4，构建卫生批次）
