# Code Time Tracker —— 项目全景解读

这是一款 **JetBrains 平台插件**，用于自动追踪编码时间并提供可视化统计分析，数据完全本地存储，注重隐私。当前版本
**v0.8.9**，已具备相当完整的功能体系。

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

---

# OpenCode Agent Instructions - Project Specific

> 本文件定义 code-time-tracker 项目的 AI 工程化规范

---

## Memory Bank Protocol

本项目使用 `memory-bank/` 目录管理设计演进脉络。

### 会话开始

- **必须**先读取 `memory-bank/progress.md` 了解当前状态
- **必须**检查 `memory-bank/implementation-plan.md` 的阶段进度
- 使用命令：`/boot`

### 会话结束

- **必须**更新 `memory-bank/progress.md` 记录进度
- **必须**在 `memory-bank/implementation-plan.md` 标记完成的阶段
- 使用命令：`/save <完成内容>`

### 重大功能开发

- **必须**先创建 Design Doc（`memory-bank/docs/XXX-*.md`）
- 使用命令：`/plan <feature-name>`
- Design Doc 包含：概述、技术设计、实现计划、风险、验收标准

---

## Commands 使用规范

| 命令                    | 场景    | 必需 |
|-----------------------|-------|----|
| `/boot`               | 新会话开始 | 是  |
| `/save <note>`        | 阶段完成  | 是  |
| `/plan <feature>`     | 重大功能  | 是  |
| `/new-feature <name>` | 开始新功能 | 可选 |

---

### `/boot` 命令 - 会话唤醒

执行 `/boot` 进行会话初始化，详细流程见 `.opencode/commands/boot.md`

---

### `/save <note>` 命令 - 休眠存档

执行 `/save <note>` 保存进度，详细流程见 `.opencode/commands/save.md`

---

## Git 提交规范

- Memory-bank 目录的变更**应该**提交（记录设计演进）
- 提交信息格式：`docs: update memory-bank - <变更内容>`
- **重要**：在执行 `git commit` 之前，必须先征得用户明确同意

---

## 版本号更新规范

每次发布（提交到主分支）**必须**同步更新版本号：

1. 更新 `gradle/libs.versions.toml` 中的 `pluginVersion`
2. 更新 `AGENTS.md` 顶部的版本号（保持一致）
3. 更新 `README.md` 中的版本 badge
4. 提交信息格式：`release: bump version to x.x.x`

**版本号规则**：
- 修复 Bug → patch 版本 +1（如 0.8.9 → 0.8.10）
- 新增功能 → minor 版本 +1（如 0.8.9 → 0.9.0）
- 破坏性变更 → major 版本 +1（如 0.9.0 → 1.0.0）

---

## 工作流示例

```
1. 打开新会话
   → 运行 /boot

2. 开始新功能
   → 运行 /plan <feature>
   → 编写 Design Doc

3. 实现功能
   → 基于 Design Doc 实现
   → 遇到问题记录到 progress.md

4. 阶段完成
   → 运行 /save <完成内容>

5. 提交代码
   → 提交代码变更
   → 提交 memory-bank 变更
```

---

## Design Doc 模板

详见 `memory-bank/docs/` 目录，文件名格式：`XXX-feature-name.md`

每个 Design Doc 必须包含：

1. 概述（背景、目标、用户故事）
2. 技术设计（架构、数据模型、API）
3. 实现计划（阶段、依赖）
4. 风险与缓解
5. 验收标准
