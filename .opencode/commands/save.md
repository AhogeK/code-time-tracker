---
description: 保存当前进度到 memory-bank
arguments:
  - name: note
    description: 进度备注（如完成的功能、遇到的问题）
    required: false
---

# 保存进度

*! date +"%Y-%m-%d %H:%M" *

请更新 `memory-bank/progress.md`：

1. 更新 **最后更新** 日期
2. 在 **最近完成** 中添加：$1
3. 更新 **当前状态**（如需要）
4. 更新 **下一步**（如需要）

如果 $1 为空，请询问用户要记录的进度内容。

同时检查 `memory-bank/implementation-plan.md`，标记已完成的阶段（如有）。
