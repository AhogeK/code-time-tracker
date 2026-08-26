# Progress

> 最后更新: 2026-08-26

## 当前状态

- **活跃功能**: 云同步基础设施 A 阶段（全部完成）
- **当前阶段**: 待提交（Settings UI 完成，版本 0.11.0）
- **阻塞问题**: 无

## 最近完成

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

- [ ] 等待用户验证 A 阶段功能（runIde 手动验收设置页）
- [ ] 云同步 B 阶段（同步客户端 push/pull）

## 笔记

- 所有测试通过 `./gradlew test`（63/63）
