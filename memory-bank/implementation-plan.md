# Implementation Plan

> 最后更新: 2026-02-19

## 阶段列表

### Phase 1: 基础设施拆分

- [ ] 创建 ConnectionManager 类，提取连接管理逻辑
- [ ] 创建 MigrationManager 类，提取建表逻辑
- [ ] 更新 DatabaseManager 使用新类

**验收标准**:

- ConnectionManager 和 MigrationManager 可独立实例化
- 现有 DatabaseManager 功能正常

### Phase 2: 数据层拆分

- [ ] 创建 SessionRepository 类，提取会话 CRUD
- [ ] 将 saveSessions, importSessions, getSessions 等移入
- [ ] 更新 DatabaseManager 委托

**验收标准**:

- SessionRepository 包含所有会话操作
- DatabaseManager 保持向后兼容

### Phase 3: 统计层拆分

- [ ] 创建 StatsRepository 类，提取所有统计方法
- [ ] 移动 getDailyCodingTimeForHeatmap, getCodingStreaks 等
- [ ] 更新 DatabaseManager 委托

**验收标准**:

- StatsRepository 包含所有统计查询
- 统计功能工作正常

### Phase 4: 清理与测试

- [ ] 删除 DatabaseManager 中的重复代码
- [ ] 添加单元测试覆盖新类
- [ ] 运行现有集成测试确保兼容性
- [ ] 代码审查

**验收标准**:

- DatabaseManager 缩减至 ~200 行
- 所有测试通过 
