# Active Context

> 当前工作上下文（保留最近30天，删除>90天前）
## [2026-08-26] - A 阶段服务容器实例化 bug 修复（版本 0.11.1）

- **症状（runIde 手动验收）**：打开设置页抛 `InstantiationException: SyncApiKeyManager does not define any of supported signatures`，级联 `ConfigurableWrapper.getConfigurable()` null → `isModified()` NPE
- **根因**：SyncApiKeyManager/SyncHttpClient/SyncApiServiceImpl 三个服务构造器带自定义服务参数，平台容器只支持 `()void/(CoroutineScope)/(Application)/(Application,CoroutineScope)/(ComponentManager)` 5 种签名；纯 JUnit 测试手动 new 从不经过容器，runIde 首次暴露
- **修复**：各加 secondary 无参构造器（内部 `ApplicationManager.getService` 具体类）；`SyncSettingsConfigurable` 的 `getService(SyncApiService)` 改 `getService(SyncApiServiceImpl)`；plugin.xml 删 3 条 applicationService 注册（`@Service` 注解即 light service，`SyncSettingsState` 的 `@State` 注解驱动持久化）
- **回归防护**：`SyncServiceResolutionTest`（LightPlatformTestCase，真实 headless 容器解析 4 个服务）；vintage engine `testRuntimeOnly` → `testImplementation`（编译期需 `junit.framework.TestCase`）
- **红绿验证**：stash 修复后测试 FAILED（同一 InstantiationException）→ 恢复后通过；64/64 测试
- 版本 0.11.0 → 0.11.1；README badge 同步

## [2026-08-26] - 登录绑定移除 + .env 构建配置 + 设置页 UI 修复（版本 0.11.1）

- **登录绑定移除**：ctt-server login 强制 hCaptcha（`captchaService.verifyCaptcha`，实测 403 VALIDATION_ERROR），插件无法完成 → 移除邮箱/密码 UI + 服务层 `bindWithCredentials`/`login`/`createApiKey` + 相关 DTO（LoginRequest/CreateApiKeyRequest/Response/ApiKeyResponse）+ 6 个测试用例；绑定只留手动粘贴；`SyncApiKeyManager` 构造器精简为仅 `settings`（删 apiService 参数）
- **前端地址构建配置**：`build.gradle.kts` `generateSyncConfig` 任务生成 `SyncWebConfig.kt`（WEB_URL/DEFAULT_SERVER_URL，build/generated 不入库）；解析顺序 `-Pctt.*` > `CTT_*` 环境变量 > 项目 `.env` > 默认；`.env.example` 模板（提交）+ `.env` gitignore；serverUrl 默认构建注入 + IDE 设置可覆盖（自部署场景）；设置页 `Get an API key` 按钮（`BrowserUtil.browse(SyncWebConfig.WEB_URL)`，固定链接非可编辑字段）
- **设置页 UI 修复**（runIde 验收发现）：
  - **modality**：`invokeLater` 默认 NON_MODAL 在 modal 设置对话框内不执行（"按钮灰 + 没反馈"根因）→ 全部后台跳转改 `ModalityState.stateForComponent(panel)`，`panel` 字段在 createComponent 末尾赋值
  - **setBusy(false) 恢复绑定状态**（unbind=apiKeyPrefix!=null, test=!bound）——修复 unbind 按钮误亮
  - **isBound() 移出 EDT**：PasswordSafe.get 在 2026.1 是 slow operation（EDT 禁止，日志 SEVERE）→ `refreshBindingState` 后台读凭据库 + invokeLater 回 UI
  - **statusLabel**：操作结果绿（`JBColor(0x1B7F3B,0x4E9A51)`）/红（`UIUtil.getErrorForeground()`）显示 + `javax.swing.Timer` 5 秒自动清除（替代 modal 内不显示的 balloon notify）；test/绑定/解绑全部接入
- **教训**：edit 工具多操作 payload 的 MATCH 不匹配易产生语法损坏 → 复杂/多段改动用 `write` 重写小文件更稳；2026.1 `JBUI` 包从 `com.intellij.ui` 迁移到 `com.intellij.util.ui`
- 测试 58/58（删 6 个登录用例）；版本 0.11.1（与服务容器修复同批未提交）

## [2026-08-26] - A3 五轴审查修复（独立子代理抓出 2 个真实 bug）

- **状态一致性 bug**：绑定/解绑改 settings.syncEnabled 但 checkbox 不刷新 → OK/Apply 静默回退刚绑定状态 → refreshBindingState 同步 checkbox
- **测试连接误导**：ping 用已保存 URL 而非编辑中的字段 → 临时切换 settings.serverUrl（try/finally 恢复）
- **凭据卫生**：绑定成功后清空密码/手动 key 字段（成功时）
- Nit：Regex 提升 companion 常量；删除空 dispose()（Disposable 无资源）；isBound() 去重（vault.load 一次）
- 63/63 测试通过；版本 0.11.0

## [2026-08-26] - A3 设置界面完成（版本 0.11.0）

- `ui/SyncSettingsConfigurable.kt`：applicationConfigurable 设置页——服务器地址/同步开关/API Key 绑定（登录 + 手动粘贴）/解绑/测试连接；EDT 规则（后台线程 + invokeLater）；Notification 提示（plugin.xml 注册 notificationGroup）
- plugin.xml 注册 applicationConfigurable + notificationGroup（SyncApiService 注入用于 pingServer）
- **教训**：edit 工具的 ＋ 行标记在本文件写成字面 `+` 字符（全角＋未识别）→ 用 python 修复；plugin.xml 类文件改动用 python 或谨慎 edit
- 版本 0.10.0 → 0.11.0；编译 + 63/63 测试通过
- A 阶段全部完成：A1 transport ✅ A2 credentials ✅ A3 settings ✅ A4 error mapping ✅

## [2026-08-26] - 方案 A：只支持新版 IDE（版本 1.0.0）

- **sinceBuild 251 → 261**（IDEA 2026.1 起），`create("IU","2026.1")`；放弃 2025.x 用户（用户决策）
- **JBR 事实**：2025.3 = JBR 21.0.8（本机 SDK 实测）；2026.1 = JBR 25（JBR 版本表）——261 是最早带 JBR 25 的 IDE
- **编译目标 21 → 25**：JavaCompile `--release 25` + Kotlin `jvmTarget 25`（Kotlin 2.4.10 支持 JVM_25/26）
- **坑**：IPGP 对 Kotlin 任务应用 `jvmTarget.convention()`（弱默认）覆盖 `kotlin {}` 扩展设置 → 必须**任务级 `configureEach { compilerOptions.jvmTarget.set() }`**
- **坑**：JavaCompile 的 release 由 Gradle daemon JDK 决定（JAVA_HOME 不重启 daemon 不生效）→ 声明 **java toolchain 25**（可移植）
- **坑**：UserManager 隐式依赖 IDE 平台传递的 okio（`lock.withLock`）——2026.1 SDK 不再传递 → 改为标准 `ReentrantLock` try/finally
- 产物 class major 69（Java 25）；63/63 测试通过
- 文档全量同步：README（badge/Requirements）、CONTRIBUTING（JDK 25 + IDEA 2026.1）、plugin.xml description、AGENTS.md R8、techContext/projectbrief

## [2026-08-26] - 依赖与工具链升级（版本 0.9.1）

- **gradle-versions-plugin 0.53 → 0.61.0**：坐标迁移 `com.github.ben-manes.versions` → `io.github.ben-manes.versions`（旧坐标停在 0.54，README badge 指向新坐标；portal metadata 旧坐标滞后是陷阱）
- **Gradle 9.3.1 → 9.7.1**（wrapper）
- **依赖**：gson 2.14.0、junit 6.1.3、sqlite-jdbc 3.53.2.1、IPGP 2.18.1
- **Kotlin 2.2.21 → 2.4.10**：JDK 25 必需（2.2.21 在 JDK 25 下 Kotlin daemon `NoSuchMethodError: connectAndLease`，靠 in-process fallback 才编译）
- **JDK 25 LTS 验证**：本机 sdkman 25.0.4-tem 构建 BUILD SUCCESSFUL + 63/63 测试；**jvmTarget 保持 21**（产物 major 65，插件运行在 IDE JBR 21）——构建环境 25、编译目标 21
- 全量测试 63/63 通过；版本 0.9.0 → 0.9.1

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
