---
description: 智能唤醒工作流：环境嗅探、记忆挂载与按需时效性同步
---

# 🚀 会话唤醒与状态同步

请严格按照以下顺序执行初始化操作，确保你对本项目活在"当下"且具备完整的上下文：

## 1. 环境嗅探

- 全局快速扫描当前代码库结构（`src/` 目录）
- 识别项目使用的核心第三方库（查看 `build.gradle.kts`）

## 2. 记忆挂载

- 读取 `memory-bank/progress.md` 获取当前状态
- 读取 `memory-bank/implementation-plan.md` 获取阶段进度
- 读取 `memory-bank/decisions.md` 了解关键决策

## 3. Git 远端同步前置检查

执行以下命令确保本地代码库是最新状态：

```bash
git fetch
git status
```

- 若发现远端有更新（`git status` 显示 "behind"），执行拉取并处理可能的冲突：

```bash
git pull --rebase
```

- 若拉取过程产生冲突或代码变更，必须先重新运行质量门控后再继续开发

## 4. 状态汇报

根据 progress.md 的内容汇报：
- 当前活跃功能
- 阻塞问题
- 今日目标
- 建议立即执行的 3 个动作

---

> **注意**：如果 progress.md 或 implementation-plan.md 为空或过于陈旧，请主动更新它们。
