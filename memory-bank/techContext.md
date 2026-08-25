# Tech Context

> 技术栈与项目结构

## 技术栈

| 维度 | 选型 |
|---|---|
| **开发语言** | Kotlin (JVM 21) |
| **构建工具** | Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin |
| **目标平台** | JetBrains IDE 2025.3+ (Build 253+) |
| **本地存储** | SQLite（via `sqlite-jdbc`） |
| **数据序列化** | Gson |
| **日期选择器 UI** | LGoodDatePicker |
| **测试框架** | JUnit 5 + AssertJ |
| **静态分析** | Qodana |

## 包结构

```
codetimetracker/
├── action/        # IDE Action（菜单/工具栏操作入口）
├── activity/      # 用户活动事件订阅
├── database/      # SQLite 数据持久化层
├── handler/       # 事件处理器
├── listeners/     # IDE 生命周期监听器
├── model/         # 数据模型（CodingSession / Stats / TimePeriod）
├── service/       # 核心业务逻辑层
├── statistics/    # 统计分析计算
├── toolwindow/    # 工具窗口注册
├── topics/        # 消息总线 Topic 定义
├── ui/            # 自定义 Swing 组件
├── user/          # 用户标识管理
└── widget/        # 状态栏 Widget
```

## 版本信息

- `pluginVersion`：0.8.10（`gradle/libs.versions.toml` 单一来源，`build.gradle.kts` 引用，禁止硬编码）
- 版本规则见 AGENTS.md R18

## 依赖变更记录

- （待记录，新增/升级依赖后在此登记）
