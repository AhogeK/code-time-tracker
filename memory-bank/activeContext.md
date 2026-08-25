# Active Context

> 当前工作上下文（保留最近30天，删除>90天前）

## [2026-08-26] - 审查修复批次（code-review + 真实集成验证）

- **审查发现并修复**：
  - plugin.xml 注册 4 个 applicationService（light service 不支持依赖构造注入，官方文档证实；运行时 getService 此前会失败）
  - **envelope 契约修复（集成测试抓出）**：ctt-server 统一响应是 `{success,message,data:{...},timestamp}` 包裹结构，成功/错误解析均需走 `RestApiEnvelope`；错误码在 `data.code`（实测 401 AUTH_003 验证）
  - 429 retryAfter 加 60s 上限（超限直接 RATE_LIMITED，防阻塞调用线程）
  - `toUserMessage()` 优先返回具体 message（空白键提示不再误导）
  - 手动粘贴加 `cttak_` 前缀校验；移除冗余 cast
  - **S3 判定不成立**：2025.3 的 `State` 注解无 roamingType 参数（reviewer 知识过时，已回退）
- **真实集成测试** `SyncApiIntegrationTest`（assumeTrue 服务器可达，2 测试实跑本地 ctt-server）
- **冒烟脚本** `scripts/verify-sync-flow.sh`：注册→Mailpit 验证（HTML token 提取，参考 ctt-web get-token.sh）→登录→建 SYNC key→probe 403 AUTH_020，实跑通过；真实 rawKey 格式 `cttak_*` 确认
- **版本 0.8.10 → 0.9.0**（R18 微调：插件发布制品不用 -SNAPSHOT 后缀）；README badge 同步
- 全量 63/63 通过（含 4 个新增用例）

## [2026-08-26] - 规则更新：R19 自我学习 v2 + 全局 4.3

- 项目 R19 v2：触发条件扩为"同一问题解决 2 次以上 **或** 单次排查耗时 >30 分钟且有复盘价值"；动作改为**先询问用户**再创建，禁止自动写入；明确与 R20 的衔接（询问环节 = 用户明确要求）
- 全局 `~/.omp/agent/AGENTS.md` 新增 4.3（同款跨项目规则）

## [2026-08-26] - 规则更新：禁用内置 WebSearch

- 全局 `~/.omp/agent/AGENTS.md` 新增 4.2 节：禁止 WebSearch 工具，网络检索走 doko-*/opencli/grep.app 浏览器工具链（本地 bridge + 用户 Chrome，禁 Incognito）
- 项目 AGENTS.md R26 升级 v2（同规则项目版 + gemini/perplexity 咨询保留）
- 背景：2026-08-26 排查 IPGP 问题期间内置 WebSearch 全部 provider 失败，opencli google search 与 dokobot 本地模式可用且质量更高

## [2026-08-26] - 同步基础设施 A 阶段（Transport + Credentials 完成）

- 新增 `service/sync/`：SyncError（错误模型+映射器+Retry-After 双源解析）、SyncDtos、SyncSettingsState（@State 持久化）、SyncHttpClient（JDK HttpClient+429 重试）、SyncApiService/Impl（login/createApiKey/ping）、SyncKeyVault（PasswordSafe 门面）、SyncApiKeyManager（绑定/解绑/状态）
- 新增 `ui/` 前的 Java 桥 `PasswordSafeCompat.java`：K2 编译器无法解析 2025.3 PasswordSafe 的 @JvmStatic bridge（javac 正常）——Kotlin 侧调用走 Java 门面
- **IPGP 升级 2.11.0 → 2.17.0**：修复模块别名解析回归（#2144）；credentialStore 模块在 app.jar 内无法 bundledModule 声明，改用 `IntelliJPlatformExtension.platformPath` 取 app.jar 加入 compileOnly（运行时 IDE 提供，不打包）
- **API 迁移坑**：2025.3 中 `CredentialAttributes` 从 `com.intellij.ide.passwordSafe` 移到 `com.intellij.credentialStore` 包
- 测试 25 个新增，全量 59/59 通过
- 待办：Settings UI（SyncSettingsConfigurable + plugin.xml）、版本号 0.9.0-SNAPSHOT、README、提交授权

## [2026-08-26] - 新建 SKILL_GRAPH.md 技能索引

- 实盘核对 3 个技能来源：内置注册表 397 + `~/.agents/skills/` 371 + `~/.config/opencode/skills/` 88
- 去重：25 个同名双目录（arkcli-* 24 + notion-mcp）、50 个 GStack 别名、browser 系列 3 目录合并
- 项目级技能：无（本仓库无 `.agents/skills/`）
- 不收录 opencode 宿主内置技能（omp 无法调用）

## [2026-08-26] - AGENTS.md 重构为 ctt-server/ctt-web 风格

- 重写 AGENTS.md 为"项目记忆与行为约束"格式（R1-R28）
- 新增 `projectbrief.md` / `techContext.md` / `systemPatterns.md`
- 影响：会话初始化流程改为读取 memory-bank 全部文件（R1）

## 当前状态

- 版本：0.8.10
- 活跃分支：develop
- 活跃功能：无（StatusBar 时间显示修复已完成，等待用户验证）
- 阻塞问题：无

## 下一步

- 等待用户验证 StatusBar 时间显示修复效果
