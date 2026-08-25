# AGENTS.md - 项目记忆与行为约束

> 本文件由 AI 自动维护，人类请勿手动编辑

## 核心规则

### R1: 会话初始化

每次会话开始立即读取 `memory-bank/` 下所有文件，缺失则创建。

### R2: 记忆更新（强制实时）

**严禁滞后更新** - 不在单次交互自动更新易造成断片。响应完成后**立即**评估更新：

| 触发条件 | 更新文件 | 更新内容 |
|---|---|---|
| 代码修改 | `activeContext.md` | 具体变更 + 日期前缀 |
| 任务完成 | `progress.md` | 完成项移至"已完成" |
| 架构决策 | `systemPatterns.md` | 新模式/设计决策 |
| 技术栈变化 | `techContext.md` | 依赖/版本变更 |
| 项目变更 | `README.md` | API/功能/里程碑 |

**更新格式**：`[YYYY-MM-DD] - 变更标题` + 文件/影响说明

### R3: 关联项目（只读红线）

`../ctt-server`（后端）与 `../ctt-web`（前端）均为**只读**关联项目：涉及云同步/API 对接、DTO 契约、认证协议、响应格式变更时**主动读取** ctt-server 对应源码验证契约（R10），不猜测接口形状。当前 `CodingSession` 已预留 `isSynced`/`syncVersion` 字段，同步功能尚未实现。

**严禁修改关联项目任何文件**（含源码/测试/文档/版本号）。后端能力不足、契约需变更时，以**需求文本**形式提出（现状/期望行为/理由/影响面），由用户决策实施。

### R4: README 同步

重大变更（功能/架构/部署/里程碑）时同步更新 README.md 和版本号。

### R5: Git 提交同步

记忆文件与业务代码同 commit，禁止单独提交"更新记忆"。

### R6: Git 操作确认（强制）

**核心原则：单次交互授权。提交授权仅限当前变更，用完即失效。**

#### 授权范围

| 操作类型 | 授权有效期 | 说明 |
|---|---|---|
| `git commit/push` | **仅当前变更** | 授权仅针对用户指明的那些文件/变更 |
| `git status/log/diff/show` | 无需授权 | 只读操作，可自主执行 |
| `git branch` | 仅创建分支 | 不包含后续 commit/push |
| `gh pr create/merge` | 需单独授权 | 与 commit 授权独立 |

#### 触发关键词

| 关键词 | 含义 | 示例 |
|---|---|---|
| `提交` / `commit` | 执行 commit（不含 push） | "提交这个变更" → 仅 commit |
| `推送` / `push` | 执行 push（不含 commit） | "推送" → 仅 push |
| `提交并推送` | commit + push | "提交并推送" → commit 然后 push |
| `提交然后X` | commit + 继续X | "提交然后继续开发" → commit + 开发 |
| `检查/查看/review` | 只读 | "检查一下" → 只读，不执行 |
| `做吧/继续` | 执行需确认 | 明确动作 + 变更范围 |

#### 红线（绝对禁止）

1. **授权不延续**：用户对变更 A 的授权 ≠ 对变更 B 的授权
2. **确认性话术不算授权**："可以" / "没问题" / "通过" / "审查通过" / "看起来不错" — 必须含明确动作词（提交/commit/推送/push）
3. **连续开发不算授权**：完成 X 后开发 Y，Y 需要新授权
4. **工具建议不算授权**：code reviewer 说"可以提交" ≠ 用户授权

#### 越界示例

| 场景 | 用户说了什么 | AI 能做的 | AI 不能做的 |
|---|---|---|---|
| 用户授权变更 A | "没问题，提交并推送" | 提交变更 A 并推送 | 提交变更 B（新开发的内容） |
| 用户审查变更 A | "审查通过，可以提交" | 提交变更 A | 提交变更 A + memory-bank（用户没说） |
| 用户说继续 | "继续开发" | 开发变更 B | 开发完直接提交变更 B |
| 用户开发新功能 | "开发 XX 功能" | 开发完成，等待审查 | 开发完自动提交 |

#### 执行前强制自检

**每次 commit/push 前必须逐项检查：**

```
□ 用户是否在【当前交互】中说了"提交/commit/推送/push"？
□ 用户说的是提交【哪些变更】？（确认文件范围）
□ 是否有用户未明确授权的额外变更？（memory-bank 除外，见 R5）
□ 用户是否说了任何模棱两可的话？（"可以"、"没问题"→ 不算授权）
```

**任何一项不确定 → 停下来问用户。**

### R7: 提交规则（强制）

**核心原则：原子化提交、版本同步、AI独立、cherry-pick合并。**

- **原子化**：代码修改触发版本号更新（检查所有相关文件一并更新），版本提交独立且晚于代码提交
- **累计更新**：不用刻意提交每次中途更新，可只提交最后的版本描述跳过中途
- **提交顺序**：功能代码 → 版本号更新 → AI记忆记录（禁止版本早于功能、混合提交）
- **提交拆分**：原子性一致可合并，不过分拆分刷提交，不过于宽泛堆积大量文件
- **分支顺序**：优先完成 develop 全部提交再考虑 master
- **AI独立**：AI相关内容（memory-bank等）单独提交，不与代码混在一起
- **master合并**：非AI内容单独cherry-pick进master，严禁整条分支合并（导致AI污染），严禁错误cherry-pick旧develop导致污染

**提交信息格式**：`feat(scope): 功能描述` / `fix(scope): 根因+修复+验证` / `refactor(scope): 描述` / `chore: bump version to X.Y.Z` / `docs(memory-bank): record implementation`

**最终清理**：功能commit → 版本commit → AI记忆commit → 推送 → 验证 `git status` 干净 → 有残留立即补提交

### R8: 技术决策确认

**禁止擅自修改**：Kotlin/JVM 版本、IntelliJ Platform 版本（2026.1+ / Build 261+）、架构设计、数据模型/SQLite schema、plugin.xml 配置、依赖版本。原则：只读取不猜测，只实现不决策，有疑问必须问。

### R9: 项目一致性优先（强制）

**核心原则：按项目来而不是任务需求，需求要变通符合项目的一致性。**

| 场景 | 错误 ❌ | 正确 ✅ |
|---|---|---|
| 数据库访问 | 任务说新建就新建 DAO | 复用现有 Repository 模式（SessionRepository/StatsRepository） |
| 命名/结构 | 按任务需求创建 | 遵循项目现有 `*Service`、`*Repository`、`*Manager` 模式 |
| 注释 | 解释"代码做了什么" | 遵循 Clean Code（R11），删除冗余注释 |

执行前检查：任务是否与现有模式冲突？是否应复用而非新建？

发现冲突 → 暂停 → grep搜索现有模式 → 向用户确认 → 按项目一致性调整

### R10: 边界原则

- **不懂就问**：不确定时停下来问用户，禁止盲目猜测
- **讨论信号**：用户提出"为什么/能不能/是否应该/你看呢"类问题 = 讨论与确认信号，先给分析+方案，**确认后才实施**；严禁把质疑性提问当作实施指令
- **现代 Kotlin**：优先 `data class`、`val` 不可变、`sealed class`、pattern matching，避免全局可变状态
- **验证优先**：不确定内容先验证再使用
- **变更溯源**：发现与预期/记忆不一致时，优先猜想"是否被用户修改了"而非"AI 忘了改/改错了"
  - **第一步**：检查 git diff/commit history 确认变更来源
  - **第二步**：验证当前业务逻辑是否正确（测试通过 = 逻辑正确）
  - **第三步**：如用户修改了，更新记忆适应新逻辑，不要恢复"旧版本"
  - **禁止**：笃定"这应该是错的"、"之前改的忘了恢复"等揣测性结论

### R11: 代码规范

- **语言**：代码/注释/日志强制英文，仅 `.md` 可中文
- **注释（Clean Code）**：✅ 公共 API KDoc/复杂算法Why/警示信息；❌ 解释代码做了什么/冗余注释/注释掉的代码/TODO/FIXME
- **Kotlin 风格**：`val` 优先、`data class`、不可变集合；`@Service` 生命周期由 IDE 管理，禁止手动 new 应用级单例服务
- **Swing/EDT**：UI 组件必须在 EDT 创建/更新，耗时操作放后台线程（`invokeLater`/`executeOnPooledThread`），禁止阻塞 EDT
- **命名**：PascalCase(类)、camelCase(方法/属性)、UPPER_SNAKE_CASE(常量)、全小写(包)
- **测试**：JUnit 5 + AssertJ 多断言链式调用 `.isX().isY().isZ()`，方法名 `shouldX_whenY`
- **编辑前验证（强制）**：先读完整文件 → 编辑后 LSP diagnostics → 编译验证 → 运行相关测试

### R12: 任务规划（强制）

多步骤任务（3步以上）必须先创建todo list，规划后再执行，完成后清理。

### R13: 文件管理（强制）

禁止创建临时文件：❌ 重定向到文件（`> output.log`），❌ `.log/.txt/.tmp`；✅ 输出到控制台。任务完成检查是否误创建文件，发现立即删除。

### R14: 依赖管理（强制）

禁止擅自添加依赖（`gradle/libs.versions.toml`）。添加前必须提供分析（目的、选型理由、影响评估、替代方案）并获得用户同意。红线：禁止冗余依赖，禁止重复功能包，优先复用现有依赖。

### R15: 数据库规范（强制）

- SQLite schema 变更**必须**通过 `MigrationManager` 增加迁移脚本，禁止直接修改建表语句破坏已有用户数据
- 数据模型变更（`CodingSession`/`Stats` 等）需同步统计逻辑与 UI 展示
- `CodingSession` 云同步预留字段（`isSynced`、`syncVersion`）语义不可滥用

### R16: 记忆文件维护（强制）

| 文件 | 超限处理 |
|---|---|
| activeContext.md | 保留最近30天，删除>90天前 |
| progress.md | 已完成项归档 |
| systemPatterns.md | 合并相似模式 |
| techContext.md | 删除过时配置 |

### R17: AGENTS.md 自更新（强制）

触发条件：规则漏洞/用户新约束/重复错误需固化。更新流程：记录问题 → 添加/修改规则 → 记录到activeContext.md → 等待用户确认。命名：新增用 `R{n}`，修改保留序号+版本说明，删除标记 `[已废弃]`。

### R18: 版本号管理（强制实时）

**核心原则：任何代码变更必须同步更新版本号，严禁滞后更新（防断片）。**

版本号位置：`gradle/libs.versions.toml` 的 `pluginVersion` 字段（`build.gradle.kts` 通过 `libs.versions.pluginVersion` 引用，单一来源）

格式：`MAJOR.MINOR.PATCH`（本插件为 JetBrains 发布制品，不使用 `-SNAPSHOT` 后缀；开发中版本按变更类型正常推进，正式发布时以最终版本号提交）

变更规则：Bug修复→PATCH+1，新功能→MINOR+1，破坏性→MAJOR+1

执行时机：每次代码修改后立即：1.确定新版本号 2.更新 libs.versions.toml 3.同步 README.md 版本 badge 4.全局搜索检查硬编码 5.记录到activeContext.md

禁止：代码变更不更新、跳版本、未经确认升MAJOR、代码硬编码版本号（统一引用 `pluginVersion`）

### R19: 自我学习（强制 v2）

触发条件（满足其一）：
- 同一问题解决 2 次以上
- 单次排查/研究耗时较长（>30 分钟）且有复盘价值

动作：**先询问用户**是否沉淀为 skill，用户同意后才创建，**禁止自动写入**。

存放位置：`.agents/skills/[skill-name]/SKILL.md`

创建流程：确认解决 → 询问用户 → 用户同意 → 用skill-creator创建 → 写入.agents/skills/ → 更新版本号

**与 R20 的关系**：R20 的"仅当用户明确要求时才可操作 `.agents/`"即本规则的询问环节，二者一致不冲突。

### R20: AI 文件保护（强制）

**禁止修改 `.agents/` 目录** — 该目录是 AI 技能工作区，不是项目代码的一部分。

- ❌ 禁止读取、修改、删除 `.agents/skills/` 下任何文件
- ❌ 禁止因"发现问题"而改动 skill 文件
- ❌ 禁止将 `.agents/` 纳入代码审查或重构范围
- ✅ 仅当用户明确要求时才可操作

**红线**：即使 skill 文件有问题（过时/错误/冗余），也不得自行修改，只能提醒用户。

### R21: 分支管理（强制 - 防止生产事故）

**核心原则：master 是生产分支，永远保持干净（无 AI 文件）。**

| 分支 | 用途 | 允许 | 禁止 |
|---|---|---|---|
| master | 生产环境 | 业务代码/测试/文档/版本号 | AI文件（memory-bank/.agents/.opencode/AGENTS.md） |
| develop | 开发环境 | 业务代码 + AI文件 | 无 |

**master同步规则**：从固定起点 → `git rm -rf` AI文件 → cherry-pick develop（排除 `docs(memory-bank)`） → 冲突处理（memory-bank冲突用 `git rm -f`，版本号冲突用 `--theirs`） → 验证（`git ls-files` 无AI文件 + `./gradlew build` 通过）

**禁止操作**：直接merge develop、在master创建AI文件、在master提交docs(memory-bank)、`git reset --hard develop`、反向cherry-pick（master→develop）、在master直接修改代码

**事故恢复**：立即停止 → 确认污染 → 重置到安全点 → 重新cherry-pick → `--force-with-lease`推送 → 记录事故

**提交前审查清单**：项目一致性（grep现有模式） + Clean Code（无冗余注释） + 测试覆盖 + 编译通过 + 无回归 + 覆盖率≥80%

**常见错误预防**：重复方法→编辑后grep+LSP；字段/枚举冲突→新建前搜索复用；冗余注释→删除"解释做了什么"；测试遗漏→新增逻辑立即创建测试；命名不一致→搜索现有模式

### R22: Git 恢复禁止（强制）

**禁止执行 git reset 恢复到初始状态** — 这会导致工作丢失且不可恢复，必须经由用户确认。

### R23: 资源清理（强制）

占用资源的工具/服务使用后必须关闭。持续服务需后台静默启动，日志单独输出至文件，避免超时/资源堆积。任务完成后立即清理。

### R24: 文件阅读原则（强制）

片段读取无法解决时直接读取整个文件，改文件前必须详细阅读原文件。不反复片段读取同一文件。

### R25: Skills 选择规范（强制）

使用某类型Skills前先列出所有同类Skills，可同时加载多个，不是只能选一个。

### R26: 网络检索与外部 AI 咨询（强制 v2）

- **禁止使用内置 WebSearch 工具**（多 provider 不稳定/易失败）
- 网络检索必须走浏览器类工具链：`doko-search`/`dokobot`（本地 bridge + 用户 Chrome）、`opencli <site> search`（站点适配器）、`grep.app`（代码搜索）；使用前按 R25 列出同类技能并读对应技能文档
- 已知 URL 优先内置 `read` 直读（静态页）；需要已登录/JS 渲染页面才用浏览器工具
- 浏览器行为必须发生在用户在用或包含用户数据的浏览器上，禁止 Incognito 模式
- 高级 AI 咨询保留：可使用 `ai-chat-browser` / `opencli` 访问 gemini.google.com / perplexity.ai（需选择模型）

可使用skills访问 gemini.google.com / perplexity.ai 咨询高级AI（需选择模型）及网络搜索。

### R27: 子任务只读约束（强制）

审查/检查/调查类子任务（code-review、explore 等）必须**严格只读**：禁止执行任何 `--fix` 类命令（格式化/ktlint --fix 等）或文件写入。需要验证时仅允许只读检查（编译、运行测试等不修改源码的操作）。

红线：子 agent 运行 `--fix` 会全项目格式化污染工作区（先例：2026-08-11 ctt-web 审查事故，16 个无关文件被重排）。

### R28: AI 身份与职责边界（强制）

AI 身份：**code-time-tracker 插件开发者**。

- **唯一可写仓库 = code-time-tracker**（src/、build.gradle.kts、gradle/libs.versions.toml、memory-bank/、docs/、README.md、AGENTS.md）；跨仓库（ctt-server、ctt-web 等）一律只读 + 提需求（R3）
- **架构/契约级变更**（数据模型、同步协议、数据库 schema、跨模块设计）：先出方案+影响分析，经用户明确授权后实施
- **讨论 ≠ 指令**（R6/R10）："审查通过"≠执行授权；用户提问"为什么不能/能不能"时先分析，不得直接动手

## 执行流程

会话开始 → 读memory-bank → 创建todo（如需）→ 处理请求 → 清理临时文件 → 更新记忆 → 检查行数修剪 → 代码修改+编辑验证 → 提交前审查+Git授权+版本号

## 约束

1. 文件读写由AI自主完成
2. 记忆文件≤200行
3. 只记录已发生事实，不猜测
4. 变更即时更新
5. **项目一致性优先**（R9）
6. **编辑前必须验证**（R11）
7. **提交前必须审查**（R21）
8. 截图保存至 `/Users/ahogek/Pictures/screenshots`

## 记忆库结构

`memory-bank/`：projectbrief.md（目标）、techContext.md（技术栈）、systemPatterns.md（规范）、activeContext.md（当前）、progress.md（进度）、implementation-plan.md（阶段计划）、decisions.md（决策记录）、docs/（设计文档）
