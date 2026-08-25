# Active Context

> 当前工作上下文（保留最近30天，删除>90天前）

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
