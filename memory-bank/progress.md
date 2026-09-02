# Progress

> 最后更新: 2026-08-29

## 当前状态

- **活跃功能**: 云同步 C 阶段（同步核心）完成，端到端真实验收通过
- **当前阶段**: B 阶段（本地模型对齐 + 变更追踪）+ C 阶段（Pull/Push/游标/编排）全部完成（版本 0.17.0），收尾中
- **阻塞问题**: 无（ctt-server v0.49.0 已落地 sessionUuid 契约）

## 最近完成
- **Yearly Coding Activity 时间层级重设**（2026-09-02，版本 0.19.8）
  - heatmap visualMap.pieces 改为 <15m/15-60m/1-2h/2-5h/5-8h/>8h（gte/lt 半开区间）
  - 修复 echarts min/max 双闭区间边界 bug（恰 300s 误落 `< 5 min` 层）
  - 仅 main.js 展示层；测试 111/111


- **云同步 C 阶段：同步核心**（2026-08-29，版本 0.17.0）
  - C3 游标持久化：sync_cursor 表 + SyncCursorRepository（单调不后退）
  - C2/C1 传输：SyncApiService.pull/push（POST /api/v1/sync/pull、/push）
  - C1/C4 应用：SyncSessionApplier（UPSERT 新建/覆盖/跳过 dirty；DELETE 软删/保留 dirty）
  - 编排：SyncCoordinator.syncOnce（pull→push→pull 收敛；失败保留状态幂等重试）
  - 绑定后自动初始同步；双轴审查修复（MIN 污染/KDoc/编号注释）
  - 测试 85/85；配置缓存兼容；端到端真实验收通过（3198 会话全量同步 + 增量幂等 + 游标续传）
  - **云同步 B 阶段：本地模型对齐 + 变更追踪**（2026-08-28，版本 0.16.0）
  - B1 DTO 对齐（SyncDtos）+ B2 字段映射（SyncSessionMapper）+ B4 设备注册（需求报告 → ctt-server v0.48.0 落地）+ 变更追踪（getDirtySessions）
  - B3 软删取消（用户否决）；测试 63/63；版本 0.16.0
  - 需求报告：ctt-server SyncChangeDto.sessionUuid → v0.49.0 落地（C 阶段 pull 应用前置）

- **登录绑定移除 + .env 配置 + 设置页 UI 修复**（2026-08-26，版本 0.11.1）
  - 移除登录绑定（hCaptcha 不可行）：服务层 bindWithCredentials/login/createApiKey + 6 测试删
  - 前端地址构建配置：SyncWebConfig 生成（.env/-P/环境变量），Get an API key 按钮
  - modality/setBusy/isBound EDT/statusLabel 修复（runIde 验收）
  - 测试 58/58；与服务容器修复同批未提交
  - **A 阶段服务容器 bug 修复**（2026-08-26，版本 0.11.1）
  - 症状：runIde 打开设置页 `InstantiationException: SyncApiKeyManager does not define any of supported signatures` + `ConfigurableWrapper` NPE
  - 根因：sync 服务构造器带自定义参数，平台容器只支持 5 种签名；纯 JUnit 测试从不过容器
  - 修复：secondary 无参构造器 + 内部 getService 具体类；plugin.xml 删 applicationService 注册
  - 回归：SyncServiceResolutionTest（LightPlatformTestCase 真实容器解析），红绿验证通过；64/64 测试
  - 版本 0.11.0 → 0.11.1
  - **同步基础设施 A 阶段**（2026-08-26，版本 0.11.0）
  - A1 HTTP 传输层：JDK HttpClient + envelope 解析 + 429 Retry-After 双源 + 上限重试
  - A2 API Key 生命周期：登录→SYNC key→CredentialStore（PasswordSafe Java 桥）、手动粘贴、解绑
  - A3 设置界面：SyncSettingsConfigurable（服务器/开关/绑定/解绑/测试连接）
  - A4 错误码映射：15 个错误码 → 用户提示，状态码优先
  - 真实集成测试 + 冒烟脚本（本地 ctt-server 验证）

## 已完成归档

- **StatusBar 时间显示修复** (2026-03-17)
    - 问题：StatusBar 的 Today/This Week/This Month/This Year 显示与统计页不一致
    - 根因 1：`getTimeForPeriod()` 当 `serviceTime > 0` 时忽略数据库历史
    - 根因 2：`endTime` 基于 `finalEndTime` 计算而非实际编码时长
    - 修复：改为 `数据库时间 + 实时累积` + 用 `totalSessionTime` 计算正确的 `endTime`
    - 文件：`CodeTimeTrackerWidget.kt`, `TimeTrackerService.kt`

- **DatabaseManager 重构** (PR #8)
  - 将 1446 行 `DatabaseManager.kt` 拆分为单一职责组件
  - 新增 `ConnectionManager` (80行) - 连接管理
  - 新增 `MigrationManager` (92行) - 表结构初始化
  - 新增 `SessionRepository` (288行) - 会话 CRUD
  - 新增 `StatsRepository` (710行) - 统计查询
  - `DatabaseManager` 改为 Facade 模式 (129行)
  - 代码减少 **91%**
  - 新增 31 个单元测试 (总测试 36 个)
  - 创建 Design Doc: `memory-bank/docs/DATABASE-MANAGER-REFACTORING.md`
- 修复 `TimeTrackerService.onActivity()` 中的竞态条件
- 版本号从 0.8.8 更新到 0.8.9
- DatabaseManager 线程安全性分析 (Decision-002)
- PR #8 审查并合并

## 下一步

- [ ] 等待用户指示：D 阶段（同步触发调度：定时/IDE 生命周期事件 + 手动同步按钮 UI）
- [ ] C 阶段收尾提交（README 功能描述 + 记忆 + 设计文档）

## 笔记

- 所有测试通过 `./gradlew test`（85/85，含真实 ctt-server 集成用例）
