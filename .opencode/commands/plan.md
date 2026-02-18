---
description: 创建新功能的 Design Doc
arguments:
  - name: feature
    description: 功能名称
    required: true
---

# Design Doc: $1

请为功能 **$1** 创建一个详细的 Design Doc，包含以下章节：

## 1. 概述
- 功能背景与目标
- 用户故事

## 2. 技术设计
- 架构变更
- 数据模型变更
- API 设计（如有）

## 3. 实现计划
- 阶段划分
- 依赖项

## 4. 风险与缓解
- 潜在风险
- 解决方案

## 5. 验收标准

---

Doc 存放在 `memory-bank/docs/` 目录，文件名格式：`XXX-feature-name.md`（XXX 为序号）。

请先列出当前 docs 目录中已有的序号，再创建新文件。
