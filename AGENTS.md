# Code Time Tracker —— 项目全景解读

这是一款 **JetBrains 平台插件**，用于自动追踪编码时间并提供可视化统计分析，数据完全本地存储，注重隐私。当前版本
**v0.8.10**，已具备相当完整的功能体系。

---

## 技术栈一览

| 维度           | 选型                                                  |
|--------------|-----------------------------------------------------|
| **开发语言**     | Kotlin (JVM 21)                                     |
| **构建工具**     | Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin |
| **目标平台**     | JetBrains IDE 2025.3+ (Build 253+)                  |
| **本地存储**     | SQLite（via `sqlite-jdbc`）                           |
| **数据序列化**    | Gson                                                |
| **日期选择器 UI** | LGoodDatePicker                                     |
| **测试框架**     | JUnit 5 + AssertJ                                   |
| **静态分析**     | Qodana                                              |

---

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

---

## 核心机制

**时间追踪引擎（TimeTrackerService）**：

- `@Service(Service.Level.APP)` 应用级单例服务
- 监听编辑器键鼠事件更新 `lastActivityTime`
- 每 5 秒轮询检测空闲状态，超过 60 秒自动持久化 session
- `ConcurrentHashMap` 支持多项目并发追踪

**数据模型**：

- `CodingSession` 已预留云同步字段（`isSynced`、`syncVersion` 等）


