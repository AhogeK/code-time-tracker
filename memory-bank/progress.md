# Progress

> 最后更新: 2026-09-03
> 职责（R16）：**版本里程碑账本**——「什么版本交付了什么」。逐条变更进 activeContext；本文件不堆过程细节。
> 旧版逐条账本（0.8.9–0.17.0 过程记录）已归档：`archive/2026-09-03-progress-legacy.md`。

## 当前状态

- **版本**: 0.20.1（单一来源 `gradle/libs.versions.toml`）
- **阶段**: 云同步 A–E 全阶段交付完成；当前为契约跟进（ctt-server 版本对接）与统计口径维护
- **阻塞**: 无

## 版本里程碑

| 版本 | 交付 |
|---|---|
| 0.20.1 | Weekly Hour 重叠双计修复（`TimeRangeUtils.mergeIntervals` 抽取，统计口径统一为并集） |
| 0.20.0 | ctt-server v0.62.0 pull 分页对接（hasMore 循环 + 卡死护栏）+ 批量 upsert（单事务）+ 软删墓碑 lifting |
| 0.19.8 | Yearly heatmap 时间层级重设（gte/lt 半开区间，修边界落层 bug） |
| 0.19.7 | Heatmap 日维度重叠合并修复（union 口径） |
| 0.19.6 | 批量 push（500/批）+ 绑定重置条件修复 |
| 0.19.5 | 统计 owner 过滤补全（含未归属本地会话）+ 设备吊销自愈（404 重注册重试） |
| 0.19.4 | 构建卫生（Gradle 9.6 API）+ sqlite-jdbc 升级 3.53.4.0 |
| 0.19.3 | E2E 收敛测试（4 场景）+ 吊销 key 重绑提示 + README 云同步排障 |
| 0.19.2 | 统计账号隔离修复（换绑后数据混算） |
| 0.19.1 | 设置页布局重排 + lastSync 持久化 + 设备 id 跨 IDE 一致 + 换绑隔离 |
| 0.19.0 | 设置页同步 UI（Sync now / 状态行 / 间隔配置） |
| 0.18.0 | 同步调度（定时 5min 兜底 + IDE 生命周期 flush + 手动触发统一入口） |
| 0.17.0 | 同步核心（pull/push/游标/编排 + sessionUuid 契约落地 + 端到端真实验收） |
| 0.16.1 | 配置缓存兼容修复（GenerateSyncConfig 任务类）+ 设备注册状态 UI |
| 0.16.0 | B 阶段：本地模型对齐（DTO/映射）+ 变更追踪（getDirtySessions） |
| 0.15.0 | 绑定即注册设备 + 注册状态显示 |
| 0.13.0 | 同步契约 DTO + SyncSessionMapper + 设备查询 |
| 0.12.0 | 同步设置入口（discoverability） |
| 0.11.2 | Test connection 常驻可用 + 常量整理 |
| 0.11.1 | 服务容器修复（InstantiationException）+ 登录绑定移除 + 设置页 UI 修复 |
| 0.10.0 | 构建工具链升级（JDK 25 + IntelliJ 2026.1+ 要求） |
| 0.9.0 | 同步 HTTP 传输 + API Key 生命周期（A 阶段早期） |
| ≤0.8.9 | DatabaseManager Facade 重构、StatusBar 时间修复等 —— 见 `archive/2026-09-03-progress-legacy.md` |
