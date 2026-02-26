---
description: 开始一个新的功能开发
arguments:
  - name: feature
    description: 功能名称
    required: true
---

# 开始新功能: $1

## 1. 创建 Design Doc

请使用 `/plan $1` 命令创建 Design Doc。

## 2. 更新进度

更新 `memory-bank/progress.md`：
- 设置 **活跃功能** 为：$1
- 添加 **今日目标**
- 添加到 **下一步**

## 3. 创建分支

准备创建分支 `feature/$1`（需确认后执行）。

---

请先运行 `/plan $1` 创建设计文档。
