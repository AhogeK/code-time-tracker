# SKILL GRAPH — AI Agent 技能索引

> **最后更新**: 2026-08-26
> **技能总数**: 434 个（磁盘去重后唯一技能；来源：omp 内置注册表 397 + 用户安装 `~/.agents/skills/` 371 + opencode 配置 `~/.config/opencode/skills/` 88，去重后 434）
> **项目级技能**: 无（本仓库未创建 `.agents/skills/`）
> **用途**: AI 执行任务前扫描此文件识别相关技能；人类快速定位需要的工具

## 来源标记说明

| 标记 | 含义 |
|---|---|
| **内置** | omp（oh-my-pi）内置注册表中的技能，本会话可直接使用 |
| **用户** | 用户安装技能（`~/.agents/skills/` 目录） |
| **配置** | opencode 配置技能（`~/.config/opencode/skills/` 目录） |

**去重规则**：同一技能多来源只列一行，来源列标注全部位置。opencode 宿主自带技能（playwright/git-master 等）**不收录**——omp 无法调用。

---

## 快速查找指南

| 我要做什么 | 推荐技能链 | 说明 |
|-----------|-----------|------|
| 规划新功能 | `think` → `planning-and-task-breakdown` → `implement` | 先想清楚再动手 |
| 修复 bug | `hunt` → `tdd` → `check` | 先找根因再修 |
| 代码审查 | `check` → `code-review` → `code-review-and-quality` | 多维度审查 |
| 创建 UI | `ui` → `design-taste-frontend` → `frontend-ui-engineering` | 设计先行 |
| 写文档 | `write` → `docs-update` → `documentation-and-adrs` | 去 AI 味 |
| Git 操作 | `git-workflow-and-versioning` → `create-pull-request` | 原子化提交 |
| 浏览器测试 | `gstack-browse` → `browser-harness` → `webapp-testing` | 真机验证 |
| 免费网络搜索 | `doko-search` → `dokobot` → `exa-search` | 无 API key |
| 学术写作 | `nature-writing` → `nature-polishing` → `nature-figure` | Nature 系列 |
| CLI 工具 | `cli-anything` → `cli-hub-meta-skill` | 为 GUI 应用构建 CLI |
| 部署上线 | `deploy-to-vercel` → `shipping-and-launch` → `ci-cd-and-automation` | 发布流水线 |
| 安全审计 | `security-and-hardening` → `gstack-cso` | 漏洞排查 |
| 性能优化 | `web-performance-audit` → `performance-optimization` → `debug-optimize-lcp` | 数据驱动 |
| 生物信息学 | `biopython` → `scanpy` → `scvi-tools` | 基因组分析全流程 |
| 药物发现 | `rdkit` → `datamol` → `deepchem` → `pytdc` | 分子 ML 管道 |
| 单细胞分析 | `scanpy` → `anndata` → `scvi-tools` → `scvelo` | scRNA-seq 全流程 |
| 蛋白质工程 | `esm` → `tamarind` → `diffdock` | 结构预测与对接 |
| 量子计算 | `qiskit` / `cirq` / `pennylane` / `qutip` | 按硬件选择框架 |
| 科研绘图 | `scientific-visualization` → `matplotlib` → `seaborn` | 出版级图表 |
| 文献综述 | `paper-lookup` → `literature-review` → `citation-management` | 系统性文献检索 |
| 本仓库（JetBrains 插件） | `tdd` → `check` → `gstack-investigate` → `git-workflow-and-versioning` | Kotlin 插件开发 |

---

## 分类索引

### 思维 & 规划

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `think` | 内置·用户 | 把模糊想法变成可执行计划 | 5 阶段流程：理解意图 → 探索约束 → 评估方案 → 生成计划 → 用户批准。不写代码，只做决策 | "怎么设计"、"用什么方案"、"值不值得做"、"给个方案" |
| `idea-refine` | 内置·用户 | 细化原始想法 | 通过发散/收敛思维，把粗糙想法变成精确概念。适合还在探索阶段、不确定要做什么的情况 | "我有个想法"、"帮我细化一下"、"还不确定" |
| `planning-and-task-breakdown` | 内置·用户 | 把大任务拆成可执行步骤 | 分析依赖关系，识别可并行的任务，输出有序的 TODO list。适合 3 步以上的复杂任务 | "帮我拆任务"、"这个怎么做"、"太大了不知道从哪开始" |
| `spec-driven-development` | 内置·用户 | 编码前写规格说明 | 先写 spec（需求、约束、验收标准），再按 spec 实现。适合新项目或重大功能 | "写个规格"、"先定义清楚再做" |
| `wayfinder` | 用户 | 把大项目分解为 issue 追踪 | 适合跨多个会话的大型项目，生成 issue tracker 上的决策 ticket 列表，逐个解决 | "这个项目要做很久"、"帮我规划路线图" |
| `to-spec` | 用户 | 把对话变成规格说明 | 从当前对话中提取需求，生成 spec 并发布到 issue tracker | "把刚才讨论的变成 spec" |
| `to-tickets` | 用户 | 把计划拆成 tickets | 把 plan 或对话拆成 tracer-bullet tickets，每个都声明依赖关系，发布到 tracker | "把这些变成 tickets" |
| `what-if-oracle` | 内置·用户 | 结构化 What-If 场景分析 | 4-6 分支可能性探索：最佳、最可能、最差、狂野、逆向、二阶效应 | "如果 X 会怎样"、"压力测试决策" |
| `consciousness-council` | 内置·用户 | 多视角心智议会审议 | 从多个专家视角审视问题：技术、设计、商业、用户、风险，输出综合结论 | "不同专家怎么看"、"多角度分析" |
| `prototype` | 内置·用户 | 构建一次性原型回答设计问题 | 用最短代码验证状态模型/逻辑/UI 方向是否合理，验证完即弃 | "先做个原型试试"、"这样交互对吗" |
| `grilling` | 内置·用户 | 压力测试计划/决策 | 像审问一样追问方案：假设是什么、边界在哪、失败了怎么办，直到方案经得起推敲 | "压力测试"、"审问一下"、"挑战我的方案" |
| `grill-me` | 用户 | 单人或无文档场景压力测试 | 在无外部文档依赖时对计划/设计做对抗式追问 | "审问我的计划" |
| `grill-with-docs` | 用户 | 带文档压力测试 | 结合仓库文档对计划/设计做对抗式审查，验证与文档一致性 | "对照文档审问" |
| `loop-me` | 用户 | 工作流规格审问 | 专门审问工作流规格：步骤是否完整、异常怎么处理、边界条件 | "审问这个规格" |
| `teach` | 用户 | 教授新技能/概念 | 交互式教学：解释概念、给示例、检查理解，在当前工作区内进行 | "教我"、"解释一下" |
| `scaffold-exercises` | 内置·用户 | 创建练习脚手架 | 创建练习目录结构：章节、问题、解答、解释，通过 lint 检查 | "做练习"、"创建练习" |
| `arbor` | 内置·用户 | 自主迭代优化 | 假设树细化（HTR）：对真实工件（代码/训练配方/agent harness/数据管道/提示词）按目标与评估器反复实验优化，防过拟合开发集；长时"改进这个工件"循环 | "迭代优化"、"beat baseline"、"跑实验找最优" |

### 科研方法

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `experimental-design` | 内置·用户 | 实验设计（数据收集前） | 选择设计、随机化、区组、处理组合，确保结果可解释；含对照、分层、析因、重复等 | "怎么设计实验"、"随机化"、"DOE" |
| `hypothesis-generation` | 内置·用户 | 结构化假设制定 | 从观察数据制定可检验的假设、竞争性解释、判别性预测与测量方案 | "提出假设"、"怎么验证" |
| `hypogenic` | 内置·用户 | 自动化假设生成和测试 | 用 ChicagoHAI HypoGeniC/HypoRefine 从带标签数据集做 LLM 辅助假设探索 | "数据假设"、"HypoGeniC" |
| `scientific-brainstorming` | 内置·用户 | 创意研究构思 | 独立生成 + 结构化讨论 + 显式假设 + 对抗审查，适合早期研究方向筛选 | "科研创意"、"头脑风暴" |
| `scientific-critical-thinking` | 内置·用户 | 科学声明评估 | 评估实验设计有效性、识别偏倚与混杂、GRADE 证据分级 | "这个结论可信吗"、"批判性思维" |

### 科研可视化

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `scientific-visualization` | 内置·用户 | 出版级科研图表 | Matplotlib/Seaborn/Plotly：多面板布局、不确定性/缺失数据显示、色盲安全调色板、期刊导出规划 | "论文图表"、"画图" |
| `scientific-slides` | 内置·用户 | 科研演讲幻灯片 | PowerPoint/LaTeX Beamer：结构、设计模板、时间指导、视觉验证 | "学术报告"、"演讲 PPT" |

### 代码 & 实现

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `implement` | 用户 | 基于 spec/tickets 实现功能 | 读取 spec/tickets，按 TDD 流程实现，完成后自动触发 code review | "按这个 spec 实现"、"做这个功能" |
| `incremental-implementation` | 内置·用户 | 增量交付变更 | 把大变更拆成小步骤，每步可验证，避免一次性写太多难以调试 | "这个改动很大"、"分步来做" |
| `tdd` | 内置·用户 | 测试驱动开发 | 红 → 绿 → 重构循环：先写失败测试，再写最少代码通过，最后重构 | "TDD"、"测试先行"、"红绿重构" |
| `full-output-enforcement` | 内置·用户 | 强制完整代码生成 | 禁止截断、禁止 placeholder、禁止"你可以扩展这个"，token 超限时干净分段 | "给我完整的"、"不要省略" |
| `code-simplification` | 内置·用户 | 简化代码提高清晰度 | 重构代码使其更易读、易维护、易扩展，但不改变行为 | "这段代码太复杂"、"简化一下" |
| `codebase-design` | 内置·用户 | 设计深层模块接口 | 深模块词汇表：模块边界、接口设计、依赖方向、可测试性 | "这个模块怎么设计"、"接口怎么定义" |
| `domain-modeling` | 内置·用户 | 构建/优化领域模型 | DDD 风格：识别实体、值对象、聚合根、领域事件，编写/修订 CONTEXT.md | "领域模型"、"DDD"、"业务对象" |
| `deprecation-and-migration` | 内置·用户 | 管理废弃和迁移 | 安全废弃旧 API、迁移用户、设置过渡期、决定维护 vs 下线 | "要废弃这个 API"、"迁移旧代码" |
| `migrate-to-shoehorn` | 内置·用户 | 迁移测试文件到 shoehorn | 把测试中的 `as` 类型断言迁移到 @total-typescript/shoehorn | "测试迁移"、"shoenhorn" |
| `optimize-for-gpu` | 内置·用户 | GPU 加速 Python 代码 | CuPy、Numba CUDA、Warp、cuDF、cuML、cuGraph 等；验证正确性 + 加速比 | "GPU 加速"、"CUDA"、"太慢了" |
| `modal` | 内置·用户 | Modal 无服务器 Python | 按需运行 Python（含 GPU）：AI/ML 推理、批量处理、Web 端点、定时任务 | "部署到 Modal"、"GPU 推理" |
| `performance-optimization` | 内置·用户 | 应用性能优化 | 前端/后端/查询/数据库瓶颈分析，N+1 查询修复，关键路径优化；要求前后数据对比 | "优化性能"、"太慢了"、"N+1" |
| `improve-codebase-architecture` | 用户 | 架构改进报告 | 扫描代码库，找出深层化机会（god class、错位依赖），生成 HTML 报告 | "架构改进"、"代码库体检" |
| `software-engineering-laws-and-philosophy` | 用户 | 软件工程法则 | 56 条法则指导技术决策、架构设计、时间估算、团队管理 | "工程法则"、"组织瓶颈" |

### 调试 & 问题排查

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `hunt` | 内置·用户 | 找到根因再修复 | 3 次假设限制：3 次尝试未找到根因就停下来重新分析；**不修症状，只修根因** | "报错了"、"崩溃了"、"不工作"、"以前是好的" |
| `diagnosing-bugs` | 内置·用户 | 硬 bug 和性能回归诊断循环 | 专门处理难以复现、难以定位的 bug：性能回归、内存泄漏、竞态条件 | "这个 bug 很难查"、"偶发的" |
| `debugging-and-error-recovery` | 内置·用户 | 系统性根因调试 | 测试失败、构建失败、行为不符预期时的系统化根因定位与错误恢复 | "调试指南"、"构建失败" |
| `memory-leak-debugging` | 内置·用户 | JS/Node.js 内存泄漏 | 用 Chrome DevTools 堆快照/比较定位泄漏源，OOM 分析 | "内存泄漏"、"OOM"、"内存一直涨" |
| `troubleshooting` | 内置·用户 | Chrome DevTools 连接排查 | list_pages/new_page/navigate_page 失败或服务器初始化失败时定位连接与目标问题 | "连不上"、"页面打不开" |
| `ci-fix` | 内置·用户 | 诊断修复 GitHub Actions CI | 检查 workflow 运行与日志，定位失败根因，最小修复并推送修复分支 | "CI 红了"、"构建失败" |

### 审查 & 验证

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `check` | 内置·用户 | 代码差异/PR/发布就绪审查 | 审查 diff、PR、issue 队列、发布就绪、提交、推送与项目审计；读 diff 找问题，能修的直接修 | "审查一下"、"看看代码"、"合并前检查" |
| `code-review` | 内置·用户 | 沿标准/规格两轴审查 | 双轴并行子代理：是否符合项目编码标准 + 是否符合 issue/spec 要求，并排报告 | "代码审查"、"PR 审查"、"review since X" |
| `code-review-and-quality` | 内置·用户 | 多轴代码审查 | 更全面：正确性、安全性、性能、可维护性、测试覆盖，合并前必做 | "深度审查"、"质量审查" |
| `health` | 内置·用户 | 工程健康审计 | 预算感知的指令/配置漂移、hooks/MCP、verifier 覆盖、AI 可维护性审计 | "项目健康吗"、"审计 agent 配置" |
| `review-animations` | 用户 | 审查动画/动效代码 | 按 Emil Kowalski 设计哲学审查动画：流畅度、直觉性；默认标记问题，需要证明才能通过 | "动画审查"、"动效检查" |
| `web-accessibility-audit` | 内置·用户 | WCAG 无障碍审计 | 检查语义 HTML、ARIA 标签、键盘导航、点击目标、颜色对比度，给出修复建议 | "无障碍审查"、"a11y" |
| `seo-aeo-audit` | 内置·用户 | SEO/AEO 审计 | 检查 meta 标签、结构化数据、AI 可引用性、搜索可见性 | "SEO 审计"、"搜索优化" |
| `web-performance-audit` | 内置·用户 | Web 性能审计 | 用 Chrome DevTools/Lighthouse/Core Web Vitals 分析页面性能并定位瓶颈 | "性能审计"、"加载慢" |
| `peer-review` | 内置·用户 | 结构化稿件/基金评审 | 用 CONSORT/STROBE 等报告清单评估方法学、统计有效性、报告标准 | "审稿"、"写评审意见" |
| `scholar-evaluation` | 内置·用户 | 学术工作系统评估 | 定性优先、证据可溯源的学术作品发展性评审，审计低风险评估量表 | "评估论文质量" |
| `slack-qa-investigate` | 内置·用户 | 仓库问题只读调查 | 只读模式研究代码库与文档，给出有据可查的答案，不做文件修改 | "查一下这个仓库"、"研究后回答" |

### UI & 设计

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `ui` | 内置·用户 | 生产级 UI 构建 | 有设计观点的 UI：排版、间距、响应式、可访问性、截图驱动打磨；不是模板化输出 | "做页面"、"做组件"、"不好看" |
| `design-taste-frontend` | 内置·用户 | 反模板化前端设计 | 读 brief → 推断设计方向 → 输出不模板化的界面；重设计先审计，严格预检 | "设计着陆页"、"重设计" |
| `design-taste-frontend-v1` | 内置·用户 | v1 版本（向后兼容） | 旧版设计技能，仅当项目依赖其精确行为时使用；新项目用 `design-taste-frontend` | "用旧版设计" |
| `frontend-ui-engineering` | 内置·用户 | 生产级 UI 工程 | 组件架构、状态管理、响应式、WCAG 无障碍、生产质量而非纯视觉 | "前端工程"、"组件设计" |
| `redesign-existing-projects` | 内置·用户 | 升级现有网站到高级质量 | 先审计现有设计，找出 generic/AI 感，应用高端设计标准逐个修复，不破坏功能 | "这个网站太丑了"、"升级设计" |
| `image-to-code` | 内置·用户 | 图片到代码转换 | 先生成设计参考图并深度分析，再实现网站与参考图高度一致；避免小图压缩 | "按这个设计稿做" |
| `imagegen-frontend-web` | 内置·用户 | 前端设计参考图生成 | 每个 section 生成一张独立横向参考图；构图多样、单一配色、转化导向 | "生成设计参考" |
| `imagegen-frontend-mobile` | 内置·用户 | 移动端设计概念图 | iOS/Android 原生风格屏幕概念与流程，带手机 mockup 框架；只出图不写码 | "移动端设计" |
| `brandkit` | 内置·用户 | 品牌套件图生成 | 高端品牌指南板、Logo 系统、视觉世界呈现；极简、电影感、暗黑科技等风格 | "品牌设计"、"Logo" |
| `high-end-visual-design` | 内置·用户 | 高端代理设计风格 | 定义"昂贵感"：字体、间距、阴影、卡片结构、动画；封堵廉价 AI 默认值 | "高端感"、"精致" |
| `minimalist-ui` | 内置·用户 | 极简编辑风格 | 温暖单色调、排版对比、扁平 bento 网格、柔和色彩；无渐变无重阴影 | "极简"、"温暖" |
| `industrial-brutalist-ui` | 内置·用户 | 工业粗野风格 | 瑞士印刷 + 军事终端美学；刚性网格、极端字号对比、机械界面 | "粗野"、"工业风"、"数据仪表盘" |
| `stitch-design-taste` | 内置·用户 | Google Stitch 语义设计系统 | 生成 agent 友好的 DESIGN.md：严格排版、校准色彩、非对称布局、硬件加速 | "语义设计"、"Stitch" |
| `gpt-taste` | 内置·用户 | 精英 UX/UI + GSAP 动效 | Python 驱动的真随机布局、严格 AIDA 页面结构、GSAP ScrollTrigger 动画 | "GSAP 动效"、"营销页" |
| `emil-design-eng` | 内置·用户 | Emil Kowalski 设计哲学 | UI polish、组件设计、动画决策、不可见细节——让软件感觉好的工程化原则 | "精致"、"细节控" |
| `animation-vocabulary` | 内置·用户 | 动画术语反查 | "那个弹跳的东西叫什么" → Pop in；把模糊动效描述变成准确术语 | "这个动效叫什么" |
| `diagram-design` | 内置·用户 | 技术/产品图表设计 | 架构图、流程图、时序图、ER 图、时间线、泳道图等，输出 HTML+SVG/PNG | "画架构图"、"画流程图" |
| `infographics` | 内置·用户 | 专业信息图生成 | Nano Banana Pro AI 生成 + Gemini 审查；10 种类型、8 种风格、色盲安全 | "做信息图" |
| `generate-image` | 内置·用户 | AI 图像生成/编辑 | OpenRouter 图像 API（Gemini/Seedream/Recraft/GPT-Image）：照片、插画、概念艺术、合成 | "生成图片"、"编辑图片" |
| `scientific-schematics` | 内置·用户 | 出版级科学示意图 | Nano Banana 2 生成：神经网络架构、系统图、流程图、生物通路，低于阈值才重生成 | "画科学图" |
| `latex-posters` | 内置·用户 | LaTeX 研究海报 | beamerposter/tikzposter/baposter：会议展示、学术海报 | "做学术海报" |
| `pptx-posters` | 内置·用户 | PowerPoint 研究海报 | 宏免费 .pptx 海报：物理尺寸、打印、可访问性、来源与包安全检查 | "做 PPT 海报" |

### 写作 & 文档

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `write` | 内置·用户 | 重写润色散文，去 AI 味 | **不只是润色，是让文字听起来像人写的**；删除"值得注意的是"、"总而言之"等 AI 套话；中英文均可 | "帮我写"、"润色"、"去AI味"、"发布文案" |
| `docs-update` | 内置·用户 | 代码变更后更新文档 | 检测代码变更影响的用户文档并同步更新 | "更新文档" |
| `documentation-and-adrs` | 内置·用户 | 记录决策和文档 | 创建 ADR（架构决策记录），记录为什么选这个方案 | "记录决策"、"ADR" |
| `writing-guidelines` | 用户 | 写作指南审查 | 按写作规范审查文档：风格、术语、格式、语气 | "审查写作"、"检查文档" |
| `writing-fragments` | 用户 | 原始素材挖掘 | 从对话、笔记、代码中提取可写作的素材片段 | "挖掘素材" |
| `writing-beats` | 用户 | 组装素材为节奏旅程 | 把零散素材组装成有节奏的文章结构 | "组装文章" |
| `writing-shape` | 用户 | 将素材塑形为文章 | 逐段落塑造，把素材变成完整文章 | "塑形文章" |
| `scientific-writing` | 内置·用户 | 科学稿件撰写 | 显式证据来源、报告指南覆盖、作者责任、机密性控制、本地一致性检查 | "写论文"、"学术写作" |
| `researchwrite` | 内置·用户 | 研究写作管道 | 提案优先：先写论点再写段落；四层 QA 管道（compose/revise/hybrid） | "写研究提案" |
| `literature-review` | 内置·用户 | 系统性文献综述 | PubMed、arXiv、bioRxiv、Semantic Scholar 多库检索，生成引用文档 | "文献综述" |
| `citation-management` | 内置·用户 | 引用管理 | OpenAlex/PubMed/Scholar 检索，提取元数据，生成 BibTeX，验证引用 | "管理引用"、"找引用" |
| `clinical-reports` | 内置·用户 | 临床报告撰写 | 安全性约束的草稿结构 + 本地确定性检查；仅合成/去标识输入，需合格审查 | "写临床报告" |
| `clinical-decision-support` | 内置·用户 | 临床决策支持文档 | 研究用队列、生存、生物标志物评估工件；非患者诊疗 | "CDS 文档" |
| `treatment-plans` | 内置·用户 | 治疗计划文档化 | 在临床决策已由授权人员做出后，格式化与结构校验治疗计划文档 | "写治疗计划" |
| `research-grants` | 内置·用户 | 研究基金申请 | NSF、NIH、DOE、DARPA、台湾 NSTC：预算、更广泛影响、合规 | "写基金申请" |
| `markdown-mermaid-writing` | 内置·用户 | Markdown + Mermaid 写作 | 文本图表优先的文档标准，24 种图表类型参考，9 种文档模板 | "写 Markdown"、"画 Mermaid" |
| `market-research-reports` | 内置·用户 | 市场研究报告 | 证据可溯源的市场研究 + 假设驱动市场估算/预测场景，TAM/SAM/SOM | "市场研究"、"市场有多大" |

### 文档处理

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `docx` | 内置·用户 | Word 文档操作 | 创建/读取/编辑 .docx/.dotx：目录、标题、页码、页眉、模板、批注与修订 | "做 Word 文档" |
| `pptx` | 内置·用户 | PowerPoint 演示文稿 | 创建/编辑 .pptx/.potx：布局、演讲者备注、图表、模板 | "做 PPT"、"做幻灯片" |
| `xlsx` | 内置·用户 | Excel 电子表格 | 创建/编辑 .xlsx：公式、格式、财务模型、多工作表 | "做 Excel"、"电子表格" |
| `pdf` | 内置·用户 | PDF 文件操作 | 读取/提取文本表格、合并、拆分、旋转、水印、OCR、表单填写 | "处理 PDF" |
| `markitdown` | 内置·用户 | 文件转 Markdown | Microsoft MarkItDown：Office/PDF/图片/音频/HTML/CSV/ZIP → Markdown | "转 Markdown" |
| `liteparse` | 内置·用户 | 本地文档解析 | 本地解析 PDF/DOCX/Office/图片，OCR，输出带边界框的布局 JSON；页面渲染 PNG | "解析文档"、"本地 OCR" |
| `kami` | 内置·用户 | 专业文档排版 | 简历、一页纸、白皮书、作品集、幻灯片、落地页；暖羊皮纸 + 墨蓝点缀，中日英文排印 | "做简历"、"排版"、"落地页" |
| `venue-templates` | 内置·用户 | 期刊/会议模板 | 按投稿 venue 的官方模板与格式要求准备稿件/海报/基金文档 | "投稿模板"、"页数要求" |

### Git & 版本控制

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `git-workflow-and-versioning` | 内置·用户 | 结构化 git 工作流 | **每次代码变更都用这个**：原子化提交、语义化版本、changelog、分支管理 | "提交"、"版本"、"分支" |
| `git-guardrails-claude-code` | 内置·用户 | 阻止危险 git 命令 | 设置 hooks 阻止 push、reset --hard、clean、branch -D 等危险操作 | "Git 安全"、"防误操作" |
| `create-pull-request` | 内置·用户 | 创建 GitHub PR | 遵循项目约定创建 PR：提交分析、分支管理、PR 描述 | "创建 PR" |
| `resolving-merge-conflicts` | 内置·用户 | 解决合并冲突 | 分析冲突原因，选择正确解决策略，手动解决复杂冲突 | "有冲突"、"合并失败" |
| `github-bug-report-triage` | 内置·用户 | 分类 GitHub bug 报告 | 评估 bug 报告是否可操作，识别缺失信息 | "分类 bug" |
| `github-issue-dedupe` | 内置·用户 | 检测重复 GitHub issue | 语义搜索 + 关键词匹配检测重复 issue | "有没有重复" |

### 浏览器自动化 & QA

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `browser-harness`（别名 `browser` / `browser-use`） | 内置·用户 | 一切 Web 交互首选 | 任何网页自动化、抓取、测试、站点/应用工作都先走 browser-harness；三个目录内容相同，互为别名 | "打开网页"、"测试网站"、"抓数据" |
| `browser-testing-with-devtools` | 内置·用户 | Chrome DevTools 真实浏览器测试 | DOM 检查、控制台错误、网络请求、性能分析、视觉验证；需要 chrome-devtools MCP | "浏览器测试"、"DevTools" |
| `chrome-devtools` | 内置·用户 | DevTools MCP 调试 | 通过 MCP 使用 Chrome DevTools：调试网页、自动化交互、检查网络 | "DevTools 调试" |
| `chrome-devtools-cli` | 内置·用户 | DevTools CLI 脚本 | 写 shell 脚本调用 Chrome DevTools CLI 自动化浏览器任务 | "CLI 浏览器" |
| `a11y-debugging` | 内置·用户 | 无障碍调试 | 基于 web.dev 指南，用 DevTools 检查语义 HTML、ARIA、焦点、键盘导航、对比度 | "a11y 调试" |
| `debug-optimize-lcp` | 内置·用户 | LCP 优化调试 | 专门优化 Largest Contentful Paint：慢加载、CWV、首屏主内容 | "LCP 优化"、"加载慢" |
| `webapp-testing` | 内置·用户 | 本地 Web 应用测试 | Playwright 测试本地应用：功能验证、UI 调试、截图、浏览器日志 | "测试网站"、"本地页面" |
| `ai-chat-browser` | 配置 | Gemini/Perplexity 浏览器通信 | 通过浏览器自动化与 Gemini/Perplexity 对话：发消息、切换模型、提取回复 | "和 AI 聊天"、"问 Gemini" |
| `gstack-browse` | 配置 | 快速无头浏览器 QA | ~100ms/命令的无头浏览器：导航、交互、截图、响应式检查、表单测试 | "浏览器测试"、"dogfood" |
| `gstack-scrape` | 配置 | 网页数据抓取 | 首次调用原型化抓取流程返回 JSON；后续同意图调用路由到固化脚本 ~200ms；只读 | "抓数据"、"提取页面数据" |
| `gstack-open-gstack-browser` | 配置 | 启动可见 GStack 浏览器 | 打开带侧边栏扩展的 AI 控制 Chromium，实时观察每个动作 | "打开浏览器"、"启动浏览器" |

### 部署 & CI/CD

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `deploy-to-vercel` | 内置·用户 | 部署到 Vercel | 一键部署应用/网站到 Vercel，返回预览链接 | "部署到 Vercel" |
| `vercel-cli-with-tokens` | 内置·用户 | Token 认证部署 | 用 access token 而非交互式登录部署、管理环境变量 | "用 token 部署" |
| `vercel-optimize` | 内置·用户 | Vercel 成本/性能优化 | 收集 Vercel 指标与项目配置，只优化有数据支撑的候选；账单、函数调用、构建分钟 | "Vercel 太贵了"、"成本优化" |
| `shipping-and-launch` | 用户 | 生产发布准备 | 发布前检查清单：监控、分阶段发布、回滚策略 | "准备发布" |
| `ci-cd-and-automation` | 内置·用户 | CI/CD 管道自动化 | 设置 GitHub Actions、质量门禁、自动化测试、部署管道 | "配置 CI/CD" |
| `setup-pre-commit` | 内置·用户 | Husky pre-commit hooks | 设置提交前自动 lint（Prettier）、类型检查、测试 | "设置 pre-commit" |
| `terraform-style-check` | 内置·用户 | Terraform 风格检查 | 按 HashiCorp 官方风格生成/审查 Terraform HCL | "Terraform 配置" |

### 安全

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `security-and-hardening` | 内置·用户 | 代码加固 | OWASP Top 10、输入验证、认证、敏感数据、第三方集成加固 | "安全审查"、"加固" |
| `gstack-careful` / `gstack-guard` / `gstack-freeze` / `gstack-unfreeze` | 配置 | 危险操作防护与编辑范围锁定 | careful：rm -rf/DROP TABLE/force-push 前警告；freeze：编辑限指定目录；guard：两者组合；unfreeze：解除冻结 | "安全模式"、"冻结编辑"、"解除冻结" |

### 集成 & API

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `api-and-interface-design` | 内置·用户 | API 设计指南 | 稳定 API/接口设计：REST/GraphQL 端点、模块边界、类型契约、前后端边界定义 | "设计 API"、"接口定义" |
| `mcp-builder` | 内置·用户 | 构建 MCP 服务器 | FastMCP (Python) 或 MCP SDK (TypeScript) 构建高质量 MCP 服务器，让 LLM 通过工具与外部服务交互 | "做 MCP 服务器" |
| `vercel-composition-patterns` | 内置·用户 | React 组合模式 | 组件库扩展：compound components、render props、context provider；治理 boolean prop 泛滥；React 19 变更 | "组件设计"、"组件 API" |
| `vercel-react-best-practices` | 内置·用户 | React/Next.js 最佳实践 | Vercel 工程团队性能指南：写作/审查/重构 React 与 Next.js 代码，包体积优化 | "React 最佳实践" |
| `vercel-react-native-skills` | 内置·用户 | React Native/Expo 技能 | 移动端构建：组件、列表优化、动画、原生模块 | "React Native"、"Expo" |
| `vercel-react-view-transitions` | 内置·用户 | React View Transition API | `<ViewTransition>`、addTransitionType、CSS 过渡伪元素：页面/路由/共享元素/列表重排动画 | "页面过渡"、"路由动画" |
| `web-design-guidelines` | 内置·用户 | Web 设计指南审查 | 按 Web Interface Guidelines 审查 UI 代码：可用性、无障碍、视觉规范 | "设计指南"、"审查 UI" |

### 项目管理 & 协作

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `triage` | 用户 | Issue/PR 分类 | 通过 triage 角色状态机移动 issue/外部 PR：分类、验证、必要时审问、写 agent-ready brief | "分类 issue" |
| `scheduler` | 内置·用户 | 设备提醒/本地任务 | 仅限本机提醒与轻量本地定时任务（通知、本地脚本）；不调度云端 agent | "提醒我"、"定时任务" |
| `handoff` | 用户 | 会话交接文档 | 把当前对话压缩成交接文档，供其他 agent 继续 | "交接"、"保存进度" |
| `claude-handoff` | 用户 | 交接给新代理 | 把当前对话交给新的后台 agent 继续执行 | "交给新 agent" |
| `context-engineering` | 内置·用户 | 代理上下文优化 | 优化 agent 上下文配置：规则文件、记忆策略、会话启动 | "优化上下文"、"配置 rules" |
| `dhdna-profiler` | 用户 | 认知模式提取 | 从文本提取认知指纹：思维方式、决策风格、推理模式 | "分析思维模式"、"DHDNA" |
| `autoskill` | 内置·用户 | 屏幕观察技能发现 | 通过 screenpipe（本机 3030 端口）观察屏幕，检测重复研究流程，起草新技能；仅本地运行 | "观察我做什么"、"发现技能" |
| `setup-matt-pocock-skills` | 用户 | 配置工程技能套件 | 首次使用 engineering skills 前配置 issue tracker、triage 标签词汇、领域文档布局 | "配置技能" |
| `open-notebook` | 内置·用户 | 开源 NotebookLM | 自托管 AI 研究笔记：多源摄取、笔记生成、播客、文档对话、16+ 模型提供方 | "笔记本"、"研究资料" |
| `pi-agent` | 内置·用户 | Pi 终端编码工具 | 安装 Pi、配置 provider/模型/环境变量、创建技能/扩展/包/主题、RPC/事件流集成 | "用 Pi"、"Pi 技能" |
| `gstack-learn` | 配置 | 管理项目经验 | 审查、搜索、修剪、导出 gstack 跨会话学到的经验（注意：与六阶段研究 `learn` 不同） | "学到了什么"、"经验管理" |

### 数据 & 分析

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `analysis-artifacts` | 内置·用户 | 可重复分析工件 | SQL 查询 + Python 可视化 + 摘要表，贯穿 BigQuery 数据分析过程 | "数据分析"、"BigQuery" |
| `dbt-model-index` | 内置·用户 | dbt 模型查询索引 | 提供 dbt 模型（BigQuery 表）查找索引，指导数据仓库查询 | "查数据仓库"、"dbt 模型" |
| `exploratory-data-analysis` | 内置·用户 | 科学数据 EDA | 有界本地探索：CSV/TSV/JSON 画像、NumPy/HDF5/FASTA/FASTQ、缺失/泄漏审计；未知格式 fail-closed | "EDA"、"数据探索" |
| `database-lookup` | 内置·用户 | 公共数据库 API 查询 | 科学/监管/金融数据库的显式端点、过滤、分页、来源可复现查询 | "查数据库"、"查规范" |
| `usfiscaldata` | 内置·用户 | 美国财政数据 API | 国债、每日/月度财政部声明、拍卖、利率、收支统计；免 API key | "查财政数据"、"国债" |
| `get-available-resources` | 内置·用户 | 系统资源检测 | 检测主机 CPU/内存/磁盘/调度器/容器/加速器限制，输出脱敏 JSON | "检查资源"、"有多少内存" |

### 研究 & 学习

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `research` | 内置·用户 | 针对主源调查问题 | 用后台代理研究问题，引用一手资料（官方文档、源码、规范），输出带引用的 Markdown 到仓库 | "帮我查一下"、"研究一下" |
| `learn` | 内置·用户 | 六阶段研究工作流 | 收集材料 → 消化 → 组织 → 输出；适合不熟悉的领域，输出可发表的参考文档 | "学习一下"、"深入研究" |
| `read` | 内置·用户 | 读取 URL/PDF 并总结 | 抓取网页/PDF，默认简洁总结，转换/保存/引用时输出干净 Markdown | "读一下这个"、"总结这个网页" |
| `find-skills` | 内置·用户 | 发现安装技能 | 帮你找到能做某件事的技能，支持安装建议 | "有没有做 X 的技能" |
| `ask-matt` | 用户 | 询问适合的技能/流 | 路由器：根据你的需求推荐最合适的技能组合 | "我该用什么技能" |
| `using-agent-skills` | 内置·用户 | 发现调用技能 | 元技能：会话开始或不确定时发现并调用其他技能 | "怎么用技能" |
| `exa-search` | 内置·用户 | Exa 网页搜索 | 语义搜索 + URL 批量提取，学术内容优化（research paper 分类、学术域名过滤） | "搜索学术内容"、"提取 URL" |
| `parallel-web` | 内置·用户 | 并行网页工具包 | 网页搜索、URL 提取、深度研究、结构化数据丰富、实体发现、持续监控 | "网页研究"、"深度搜索" |
| `paper-lookup` | 内置·用户 | 学术文献 API 搜索 | 11 个学术数据库：PubMed、PMC、Europe PMC、bioRxiv、medRxiv、arXiv、OpenAlex、Crossref、Semantic Scholar、CORE、Unpaywall | "找论文"、"查 DOI" |
| `paperzilla` | 内置·用户 | 论文推荐和详情 | 项目推荐、规范论文详情、Markdown 摘要、反馈与导出 | "推荐论文" |
| `research-lookup` | 内置·用户 | 研究信息检索 | 编译手稿证据包：默认 Parallel Search，深度综合用 Parallel Research | "查研究"、"背景证据" |
---

## 🧬 生物信息学 & 基因组学

### 单细胞分析

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `scanpy` | 内置·用户 | scRNA-seq 标准分析管道 | QC、归一化、降维（PCA/UMAP/t-SNE）、聚类、差异表达、可视化；RDS → h5ad 转换 | "单细胞分析" |
| `anndata` | 内置·用户 | 注释矩阵数据结构 | .h5ad 文件处理，scverse 生态系统数据格式（scanpy/scvi 的数据底座） | "h5ad 文件" |
| `scvi-tools` | 内置·用户 | 单细胞深度生成模型 | scVI 概率批次校正、迁移学习、不确定性差异表达、多模态整合（TOTALVI/MultiVI） | "深度学习单细胞"、"批次校正" |
| `scvelo` | 内置·用户 | RNA 速度分析 | 从未剪接/剪接 mRNA 动态估计细胞状态转换、轨迹方向、潜在时间、驱动基因 | "RNA 速度"、"细胞轨迹" |
| `cellxgene-census` | 内置·用户 | CZ CELLxGENE Census 查询 | 群体规模公共单细胞/空间数据：版本化元数据、基因表达切片、嵌入、参考图谱比较 | "查公共单细胞数据" |

### 基因组学工具

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `biopython` | 内置·用户 | 分子生物学工具包 | 序列操作、FASTA/GenBank/PDB 解析、系统发育、NCBI/PubMed 编程访问（Bio.Entrez） | "序列处理"、"BLAST 自动化" |
| `pysam` | 内置·用户 | 基因组文件工具 | SAM/BAM/CRAM 比对、VCF/BCF 变异、FASTA/FASTQ 读写、pileup/覆盖度 | "BAM 处理"、"VCF 查询" |
| `gget` | 内置·用户 | 快速生物信息学查询 | 20+ 数据库快速查询：基因信息、BLAST/BLAT、病毒序列、AlphaFold 结构、富集、OpenTargets、COSMIC | "快速查基因" |
| `bioservices` | 内置·用户 | 多数据库统一接口 | 40+ 生物服务（UniProt/KEGG/ChEMBL/Reactome）跨库分析与 ID 映射 | "多库查询" |
| `gtars` | 内置·用户 | Rust 基因组区间分析 | 高性能区间模型与集合代数：重叠、计数、共识、覆盖、tokenization、refget/BEDbase | "高性能基因组区间" |
| `geniml` | 内置·用户 | 基因组区间 ML | 审计本地区间工作流：BED/universe 契约校验、Region2Vec/scEmbed 计划 | "基因组区间 ML" |
| `polars-bio` | 内置·用户 | Polars 基因组区间 | BED/VCF/BAM/GFF 区间的高性能操作：重叠、最近、合并、覆盖、补集 | "基因组数据框" |
| `onekgpd` | 内置·用户 | 1000 Genomes Project | 3,202 个全基因组个体级别查询：携带者、变异、等位频率、AlphaMissense | "千人基因组" |
| `tiledbvcf` | 内置·用户 | 基因组变异存储 | TileDB 高效 VCF/BCF 摄取、增量样本、压缩存储、并行查询 | "变异数据存储" |
| `pacsomatic` | 内置·用户 | nf-core/pacsomatic 管道 | 肿瘤-正常配对 BAM 输入：samplesheet 生成、Nextflow 启动、调度器提交、失败分流 | "肿瘤基因组"、"pacsomatic" |

### 生物实验平台

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `benchling-integration` | 内置·用户 | Benchling SDK/API | 注册实体、库存、ELN 条目、工作流、Benchling Apps、Data Warehouse 查询 | "Benchling 自动化" |
| `labarchive-integration` | 内置·用户 | 电子实验笔记本 API | LabArchives ELN REST API：区域端点、签名请求、用户授权、条目管理 | "实验记录" |
| `protocolsio-integration` | 内置·用户 | protocols.io 集成 | 读取/验证/导出 protocols.io 数据；仅官方 REST/MCP 合同 + 显式 --execute 才写操作 | "实验方案" |
| `opentrons-integration` | 内置·用户 | Opentrons 协议 API | OT-2/Flex 官方 Protocol API v2：液体处理、deck/labware、运行时参数、App 分析 | "液体处理机器人" |
| `pylabrobot` | 内置·用户 | 厂商无关实验室自动化 | 多厂商液体处理统一编程；物理执行需显式操作员安全门 | "实验室自动化" |
| `ginkgo-cloud-lab` | 内置·用户 | Ginkgo Cloud Lab | 云端协议提交：无细胞/E.coli/Pichia 表达纯化、HiBiT/A280/LabChip 定量、IVT mRNA、SPR、Echo-MS | "云实验室"、"Ginkgo" |
| `omero-integration` | 内置·用户 | 显微镜数据管理 | OMERO.server：图像访问、数据集检索、像素、ROI、渲染、元数据导出 | "显微镜数据" |
| `dnanexus-integration` | 内置·用户 | DNAnexus 云基因组 | dx CLI/dxpy：应用构建、数据管理、原生工作流、dxCompiler、Nextflow | "云基因组" |

### 蛋白质 & 分子建模

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `esm` | 内置·用户 | ESM 蛋白质语言模型 | esm SDK、ESM3/ESMC 模型 ID、Forge/Biohub 推理客户端、ESMFold2 折叠 | "蛋白质结构预测" |
| `tamarind` | 内置·用户 | 云端分子设计工具集 | REST API/MCP 访问开源工具：AlphaFold/Boltz/Chai、RFdiffusion、ProteinMPNN、DiffDock、抗体设计、MD | "蛋白质设计"、"云端跑结构" |
| `adaptyv` | 内置·用户 | Adaptyv Bio Foundry API | 蛋白实验设计、提交、结果检索；FoundryClient/foundry-api-public.adaptyvbio.com | "蛋白质筛选"、"BLI/SPR 实验" |
| `molecular-dynamics` | 内置·用户 | 分子动力学模拟 | OpenMM + MDAnalysis：系统搭建、力场、能量最小化、生产 MD、RMSD/RMSF/接触分析 | "MD 模拟" |
| `glycoengineering` | 内置·用户 | 蛋白质糖基化工程 | N-糖基化序列扫描（N-X-S/T）、O-糖基化热点预测、糖工程工具（NetOGlyc 等） | "糖基化分析"、"抗体优化" |
| `diffdock` | 内置·用户 | 分子对接 | DiffDock/DiffDock-L 蛋白-小分子姿态预测：PDB/序列 + SMILES/SDF/MOL2、批量对接、虚拟筛选 | "分子对接"、"虚拟筛选" |
| `rowan` | 内置·用户 | 云端分子建模平台 | pKa/macropKa、构象与互变异构体、对接、蛋白-配体共折叠、通透性、描述符 | "云端分子建模"、"pKa 预测" |

### 化学 & 药物

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `rdkit` | 内置·用户 | 化学信息学工具包 | SMILES/SDF 解析、描述符（MW/LogP/TPSA）、指纹、子结构搜索、2D/3D 生成、反应 | "分子操作"、"SMILES" |
| `datamol` | 内置·用户 | RDKit Pythonic 封装 | 简化接口与默认值：SMILES 解析、标准化、描述符、指纹、聚类、3D 构象、并行 | "分子标准工作流" |
| `deepchem` | 内置·用户 | 分子 ML | 多样化特征化器、预构建数据集、MoleculeNet 基准；ADMET/毒性预测 | "分子 ML"、"ADMET" |
| `molfeat` | 内置·用户 | 分子特征化 | 100+ 特征化器：ECFP、MACCS、描述符、预训练模型（ChemBERTa），QSAR | "分子特征" |
| `torchdrug` | 内置·用户 | PyTorch GNN 分子 | 分子图神经网络：性质预测、自监督预训练、分子生成、逆合成、知识图谱 | "分子 GNN" |
| `pytdc` | 内置·用户 | Therapeutics Data Commons | AI-ready 药物发现数据集：注册发现、任务感知切分、评估指标、分子 oracle | "药物数据集"、"TDC" |
| `medchem` | 内置·用户 | 药物化学过滤器 | Lipinski、Veber、CNS、PAINS、NIBR、ChEMBL 结构警报、复杂度指标 | "化合物筛选"、"类药性" |

### 生物数据分析

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `bulk-rnaseq` | 内置·用户 | 批量 RNA-seq 全流程 | FASTQ → QC/修剪（FastQC/fastp）→ 比对定量（STAR/Salmon/featureCounts）→ counts 矩阵 → 差异表达 → 富集 → 出版图 | "RNA-seq 分析"、"FASTQ 到 DESeq2" |
| `pydeseq2` | 内置·用户 | 差异基因表达 | PyDESeq2：公式化设计、Wald 检验、FDR、LFC 收缩、结果可视化 | "差异表达" |
| `pathway-enrichment` | 内置·用户 | 通路和基因集富集 | ORA（Fisher/Enrichr）、GSEA（preranked）、ssGSEA/GSVA；GO/KEGG/Reactome/WikiPathways/MSigDB | "通路分析"、"GSEA" |
| `deeptools` | 内置·用户 | NGS 分析工具 | BAM → bigWig、QC（相关/PCA/指纹）、热图/剖面图（TSS/peaks）；ChIP-seq/RNA-seq/ATAC | "ChIP-seq 可视化" |
| `scikit-bio` | 内置·用户 | 生物数据工具 | 序列分析、比对、系统发育树、多样性（alpha/beta、UniFrac）、PERMANOVA、微生物组 | "微生物组分析" |
| `flowio` | 内置·用户 | FCS 流式文件解析 | Flow Cytometry Standard 2.0/3.0/3.1：元数据、事件提取、多数据集 | "流式数据" |
| `imaging-data-commons` | 内置·用户 | NCI 成像数据中心 | 公共癌症成像查询/下载：IDC 集合、DICOM、放射/病理 AI 训练集、许可 | "医学影像"、"IDC" |
| `histolab` | 内置·用户 | WSI 切片处理 | H&E 切片：组织检测、tile 提取、染色归一化；轻量快速 | "病理切片" |
| `pathml` | 内置·用户 | 计算病理学 | 本地研究用：切片加载/切片化、h5path 数据、多重荧光定量、空间图、模型推理 | "计算病理" |
| `depmap` | 内置·用户 | Cancer Dependency Map | 细胞系基因依赖评分（CRISPR Chronos）、药物敏感性、基因效应；癌症靶点验证 | "癌症依赖"、"合成致死" |
| `pydicom` | 内置·用户 | DICOM 文件处理 | 读写/检查/转换 DICOM：元数据、传输语法、像素数据、去标识 | "DICOM 处理" |
| `pyhealth` | 内置·用户 | 临床深度学习 | EHR/信号/影像数据集（MIMIC-III/IV、eICU、OMOP、SleepEDF）、任务、模型、训练器、指标 | "临床 ML"、"MIMIC" |
| `pyopenms` | 内置·用户 | 质谱分析 | 蛋白质组/代谢组全流程：特征检测、鉴定、定量、LC-MS/MS 管道 | "质谱分析" |
| `matchms` | 内置·用户 | 质谱相似性 | MS/MS 谱文件 I/O、峰过滤、谱相似度、库匹配、分数矩阵 | "代谢组学"、"谱匹配" |
| `primekg` | 内置·用户 | 精准医学知识图谱 | 基因、药物、疾病、表型的多尺度生物数据查询 | "知识图谱" |
| `neurokit2` | 内置·用户 | 生物信号处理 | ECG/EEG/EDA/RSP/PPG/EMG 预处理、事件/区间分析、多模态对齐、变异性 | "生物信号" |
| `neuropixels-analysis` | 内置·用户 | Neuropixels 记录分析 | SpikeInterface：加载（SpikeGLX/Open Ephys/NWB）、预处理、漂移校正、Kilosort4 排序、质量指标、单元整理 | "电生理"、"spike sorting" |

### 系统发育 & 基因调控

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `phylogenetics` | 内置·用户 | 系统发育树构建 | MAFFT 多序列比对、IQ-TREE 2 最大似然、FastTree；ETE3/FigTree 可视化 | "建进化树" |
| `etetoolkit` | 内置·用户 | 系统发育树工具 | ETE 4：Newick/Nexus I/O、拓扑编辑、Robinson-Foulds、进化事件、NCBI 分类 | "树操作" |
| `arboreto` | 内置·用户 | 基因调控网络推断 | GRNBoost2/GENIE3：从表达数据推断 TF-靶基因关系；支持分布式 | "GRN 推断" |
| `cobrapy` | 内置·用户 | 约束代谢建模 | COBRA：FBA、FVA、基因敲除、通量采样、SBML 模型 | "代谢建模" |

### 生物信息学平台

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `nextflow` | 内置·用户 | Nextflow 数据管道 | 构建/运行/调试 Nextflow 与 nf-core 工作流：DSL2、进程/通道、nf-test、executor（HPC/云） | "生物信息学管道"、"nf-core" |
| `lamindb` | 内置·用户 | 生物数据湖仓 | 数据集注册、谱系追踪、查询、验证、Bionty 本体注释、集合、分支 | "数据管理"、"LaminDB" |
| `latchbio-integration` | 用户 | Latch 生物信息学平台 | Latch SDK/CLI：构建注册调试工作流、Latch Data/Registry、Nextflow/Snakemake、程序化执行 | "云生物信息学"、"Latch" |
| `bgpt-paper-search` | 内置·用户 | 科学论文结构化数据 | BGPT MCP：全文提取 25+ 字段（方法/结果/样本量/质量评分），文献证据综合 | "论文数据提取" |
| `hugging-science` | 内置·用户 | 科学领域 HF 资源 | 科学数据集/模型/Spaces 目录：datasets、transformers、HF Inference API、gradio_client | "找科学模型"、"HF 科学资源" |
| `pyzotero` | 内置·用户 | Zotero 引用客户端 | Zotero Web API v3：条目/集合/标签/附件检索创建更新、导出引用、PDF 上传 | "Zotero 管理" |
| `bids` | 内置·用户 | BIDS 神经影像规范 | 整理/验证/转换 BIDS 数据集：MRI/EEG/MEG/iEEG/PET/行为、元数据、DICOM 转换 | "BIDS 数据" |

---

## 🔭 天文学 & 地球科学

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `astropy` | 内置·用户 | 天文学 Python 库 | 单位/量、坐标、FITS I/O、表、时间系统、WCS、宇宙学 | "天文计算"、"FITS" |
| `geomaster` | 内置·用户 | 地理空间科学 | 遥感（Sentinel/Landsat/MODIS/SAR）、GIS、空间统计、点云、STAC/COG、8 种语言 | "地理空间"、"遥感" |
| `geopandas` | 内置·用户 | 地理空间向量数据 | GeoSeries/GeoDataFrame：空间操作、GeoJSON/Shapefile、空间索引 | "地理数据" |
| `matlab` | 内置·用户 | MATLAB/Octave 数值计算 | 构建/审查/迁移数值工作流：数组、时间/表格数据、测试、MAT 文件、Python 互操作 | "MATLAB" |
| `sympy` | 内置·用户 | 符号数学 | 代数、微积分、方程求解、符号线性代数、lambdify/LaTeX 代码生成 | "符号计算" |
| `fluidsim` | 内置·用户 | 流体动力学模拟 | FluidSim：求解器选择、FFT/MPI、数值有效性检查、HPC 安全、输出诊断 | "CFD"、"流体模拟" |
| `pymatgen` | 内置·用户 | 材料科学工具 | 晶体结构、相图、电子结构 I/O、对称性；有界 Materials Project 查询 | "材料计算" |

---

## ⚛️ 量子计算

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `qiskit` | 内置·用户 | IBM 量子计算 | Qiskit 2.x：电路/算子、V2 Sampler/Estimator、目标感知 transpile、IBM Runtime、纠错 | "IBM 量子"、"Qiskit" |
| `cirq` | 内置·用户 | Google 量子计算 | Google Quantum AI 硬件、噪声感知电路、量子表征实验 | "Google 量子" |
| `pennylane` | 内置·用户 | 硬件无关量子 ML | 自动微分、变分算法（VQE/QAOA）、量子神经网络、多设备后端 | "量子 ML" |
| `qutip` | 内置·用户 | 量子物理模拟 | QuTiP 5：主方程、Lindblad 动力学、稳态、谱、相位空间 | "量子模拟"、"开放系统" |

---

## 🤖 机器学习 & 深度学习

### ML 框架

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `scikit-learn` | 内置·用户 | Python ML | 监督/无监督学习、模型评估、超参数调优、预处理、ML 管道 | "机器学习" |
| `pytorch-lightning` | 内置·用户 | PyTorch Lightning | LightningModule/Trainer、多 GPU/TPU、回调、日志（W&B/TensorBoard/MLflow）、分布式 | "深度学习" |
| `transformers` | 内置·用户 | Hugging Face Transformers | AutoModel、pipeline、Trainer 微调；NLP/视觉/音频多模态 | "NLP/视觉/音频模型" |
| `torch-geometric` | 内置·用户 | PyTorch Geometric | GNN：GCN/GAT/GraphSAGE/GIN、异构图、邻居采样、自定义数据集 | "图神经网络" |
| `stable-baselines3` | 内置·用户 | 强化学习算法 | PPO、SAC、DQN、TD3、DDPG、A2C；Gymnasium 环境 | "强化学习" |
| `pufferlib` | 内置·用户 | 高性能 RL | 版本感知：PufferLib 3.0/4.0 环境向量化、策略、PuffeRL 训练、评估、检查点审查 | "高性能 RL" |
| `aeon` | 内置·用户 | 时间序列 ML | 分类/回归/聚类/预测/异常检测/分割；sklearn 兼容 API | "时间序列" |
| `timesfm-forecasting` | 内置·用户 | 零样本时间序列预测 | Google TimesFM 基础模型：CSV/DataFrame/数组输入，点预测 + 预测区间；运行前预检 RAM/GPU | "时间序列预测" |

### 数据处理

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `polars` | 内置·用户 | 高性能 DataFrame | 表达式驱动、懒查询优化、并行执行、流式 out-of-core、Arrow 互操作 | "数据处理"、"pandas 迁移" |
| `dask` | 内置·用户 | 分布式计算 | 大于 RAM 的 pandas/NumPy 工作流：分布式数组/DataFrame、集群扩展 | "分布式计算"、"超内存数据" |
| `vaex` | 内置·用户 | 大表格数据集 | 十亿级行数 out-of-core：惰性求值、快速聚合、大数据可视化 | "大数据"、"十亿行" |
| `zarr-python` | 内置·用户 | 云存储 N-D 数组 | Zarr-Python 3：压缩分块数组、并行 I/O、S3/GCS（fsspec）、NumPy/Dask/Xarray 兼容 | "云数组"、"分块存储" |

### 可视化 & 网络

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `matplotlib` | 内置·用户 | 底层绘图库 | 完全自定义控制、出版级 PNG/PDF/SVG 导出、科学工作流集成 | "自定义图表" |
| `seaborn` | 内置·用户 | 统计可视化 | pandas 集成：箱线图、小提琴图、热图、成对图；探索统计关系 | "统计图表" |
| `networkx` | 内置·用户 | 复杂网络分析 | 图算法、中心性、社区检测、生成模型、多种文件格式 | "网络分析"、"社交网络" |
| `umap-learn` | 内置·用户 | 非线性降维 | 2D/3D 嵌入、聚类预处理、监督/半监督 UMAP、DensMAP、AlignedUMAP | "降维"、"嵌入" |

### 统计 & 建模

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `statistical-analysis` | 内置·用户 | 统计分析指导 | 检验选择、假设检查、效应量、功效、贝叶斯替代、APA 报告 | "统计分析"、"比较两组" |
| `statistical-power` | 内置·用户 | 样本量和功效计算 | 先验功效分析、最小可检测效应、功效曲线；公式法与蒙特卡洛模拟 | "需要多少样本"、"功效分析" |
| `statsmodels` | 内置·用户 | 统计模型库 | OLS、GLM、混合模型、ARIMA：详细诊断、残差、推断 | "统计建模"、"回归" |
| `shap` | 内置·用户 | 模型可解释性 | SHAP 值：explainer/masker 选择、特征归因、局部/全局解释 | "解释模型" |
| `scikit-survival` | 内置·用户 | 生存分析 | 右删失/竞争风险：防泄漏预处理、模型选择、概率预测、一致性指标 | "生存分析" |
| `pymc` | 内置·用户 | 贝叶斯建模 | 层次模型、MCMC（NUTS）、变分推断、LOO/WAIC、后验检查 | "贝叶斯" |
| `pymoo` | 内置·用户 | 多目标优化 | NSGA-II/III、MOEA/D、Pareto 前沿、约束处理、基准（ZDT/DTLZ） | "多目标优化" |
| `simpy` | 用户 | 离散事件仿真 | 进程、资源、中断、监控、重复实验、预热、可复现输出 | "仿真"、"排队模型" |
---

## GStack 技能套件

位于 `~/.config/opencode/skills/`（来源：配置）。除 `gstack`（路由器）与 `gstack-upgrade` 外，**omp 内置注册表以去前缀名暴露**（如 `gstack-browse` → 内置名 `browse`），括号内为内置名。注意 `gstack-health` ≠ 内置 `health`（工程健康审计）、`gstack-learn` ≠ 内置 `learn`（六阶段研究）——它们是同名不同技能。

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `gstack` | 内置·配置 | GStack 技能路由器 | 把任何 gstack 请求分发到正确技能（规划/审查/QA/发布/调试/文档/安全/设计）；浏览器 QA 指向 browse | "用 gstack"、"哪个技能合适" |
| `gstack-autoplan`（内置名 `autoplan`） | 配置 | 自动审查管道 | 读取全部 CEO/设计/工程/DX 审查技能并顺序执行，6 决策原则自动决定，最终审批门呈现品味决策 | "自动审查"、"全量 review" |
| `gstack-benchmark`（内置名 `benchmark`） | 配置 | 性能回归检测 | browse 守护进程建立基线（页面加载/CWV/资源体积），每次 PR 对比 before/after，趋势跟踪 | "性能基准"、"页面速度" |
| `gstack-benchmark-models`（内置名 `benchmark-models`） | 配置 | 跨模型基准测试 | 同一提示跑 Claude/GPT（Codex CLI）/Gemini 并排：延迟、token、成本，可选 LLM 裁判评质量 | "模型对比"、"哪个模型好" |
| `gstack-canary`（内置名 `canary`） | 配置 | 部署后金丝雀监控 | 监控线上应用控制台错误/性能回归/页面失败，周期截图对比部署前基线并告警 | "部署监控"、"verify deploy" |
| `gstack-claude`（内置名 `claude`） | 配置 | Claude Code CLI 包装 | 三种模式：Review（独立 diff 审查）、Challenge（对抗性失败模式审查）、Consult（只读咨询） | "Claude 审查"、"第二意见" |
| `gstack-context-restore`（内置名 `context-restore`） | 配置 | 恢复工作上下文 | 加载 /context-save 保存的状态，优先当前分支，跨分支回退，支持 workspace 交接 | "恢复上下文"、"where was I" |
| `gstack-context-save`（内置名 `context-save`） | 配置 | 保存工作上下文 | 捕获 git 状态、决策、剩余工作，任何会话可无缝续接 | "保存进度"、"save state" |
| `gstack-cso`（内置名 `cso`） | 配置 | 首席安全官模式 | 基础设施优先安全审计：密钥考古、供应链、CI/CD 管道、LLM/AI 安全、技能供应链、OWASP/STRIDE | "安全审计"、"威胁建模" |
| `gstack-design-consultation`（内置名 `design-consultation`） | 配置 | 设计咨询 | 理解产品 → 研究竞品 → 提出完整设计系统（美学/字体/配色/布局/动效）→ 生成 DESIGN.md | "设计咨询"、"设计系统" |
| `gstack-design-html`（内置名 `design-html`） | 配置 | 设计转 HTML | 生产级 Pretext HTML/CSS，30KB 开销零依赖；文本真实回流、动态布局 | "设计转代码"、"finalize design" |
| `gstack-design-review`（内置名 `design-review`） | 配置 | 设计审查 | 找视觉不一致/间距/层级/AI 味/慢交互，逐个原子修复并截图前后对比 | "设计审查"、"视觉 QA" |
| `gstack-design-shotgun`（内置名 `design-shotgun`） | 配置 | 设计探索 | 生成多个 AI 设计变体，打开对比板收集结构化反馈并迭代 | "探索设计"、"设计选项" |
| `gstack-devex-review`（内置名 `devex-review`） | 配置 | 开发者体验审计 | 真实测试 DX：导航文档、走入门流程、计时 TTHW、截图错误信息、DX 计分卡 | "DX 审计"、"体验测试" |
| `gstack-diagram`（内置名 `diagram`） | 配置 | 图表生成 | 英文描述/Mermaid → 三件套：源码 + 可编辑 .excalidraw + 渲染 SVG/PNG；完全离线 | "画图"、"流程图" |
| `gstack-document-generate`（内置名 `document-generate`） | 配置 | 文档生成 | 从零生成缺失文档：Diataxis 框架（tutorial/how-to/reference/explanation） | "生成文档"、"写教程" |
| `gstack-document-release`（内置名 `document-release`） | 配置 | 发布后文档更新 | 读全部文档 × diff 交叉引用，构建 Diataxis 覆盖图，更新 README/ARCHITECTURE/CHANGELOG | "更新文档"、"发布后同步" |
| `gstack-freeze`（内置名 `freeze`） | 配置 | 限制文件编辑范围 | 会话内编辑限制在指定目录，调试时防止误改无关代码 | "冻结编辑"、"只改这个目录" |
| `gstack-guard`（内置名 `guard`） | 配置 | 完整安全模式 | careful + freeze 组合：危险命令警告 + 目录级编辑限制 | "完全锁定"、"安全模式" |
| `gstack-health` | 配置 | 代码质量仪表盘 | 包装现有工具（类型检查/lint/测试/死代码/shell lint），加权综合 0-10 分，趋势跟踪 | "健康检查"、"质量评分" |
| `gstack-investigate`（内置名 `investigate`） | 配置 | 系统性调试 | 四阶段：调查 → 分析 → 假设 → 实现；铁律：无根因不修复 | "调试"、"为什么坏了" |
| `gstack-ios-clean`（内置名 `ios-clean`） | 配置 | iOS 调试桥清理 | 移除 DebugBridge SPM 包与所有 #if DEBUG 接线；Release 构建守卫是安全关键路径 | "iOS 清理" |
| `gstack-ios-design-review`（内置名 `ios-design-review`） | 配置 | iOS 设计审查 | 真机截图每屏，对照 Apple HIG/DESIGN.md 评分 0-10 并给"如何到 10" | "iOS 设计审查" |
| `gstack-ios-fix`（内置名 `ios-fix`） | 配置 | iOS Bug 修复 | 自动闭环：读源码 → 修复 → 重建 → 重部署 → 真机验证；保留回归测试 fixture | "iOS 修 bug" |
| `gstack-ios-qa`（内置名 `ios-qa`） | 配置 | iOS 真机 QA | USB CoreDevice 隧道连真 iPhone：截图 → 分析 → 决策 → 操作 → 验证循环 | "iOS 测试"、"真机 QA" |
| `gstack-ios-sync`（内置名 `ios-sync`） | 配置 | iOS 调试桥同步 | 按上游模板重新生成 StateServer/DebugOverlay/accessors | "iOS 同步" |
| `gstack-land-and-deploy`（内置名 `land-and-deploy`） | 配置 | 合并部署流程 | 合并 PR → 等 CI → 部署 → canary 验证生产健康；接在 /ship 之后 | "部署"、"merge and verify" |
| `gstack-landing-report`（内置名 `landing-report`） | 配置 | 着陆报告 | 只读队列仪表盘：VERSION 槽位占用、兄弟工作区 WIP、下一个 slot | "查看队列"、"landing report" |
| `gstack-make-pdf`（内置名 `make-pdf`） | 配置 | Markdown 转 PDF | 出版级排版：1in 边距、智能分页、页码、封面、运行页眉、弯引号、可点击目录、DRAFT 水印 | "做 PDF"、"导出 PDF" |
| `gstack-office-hours`（内置名 `office-hours`） | 配置 | YC Office Hours | 启动模式：6 个强迫性问题暴露需求现实；构建者模式：设计思维头脑风暴 | "创业咨询"、"想法评估" |
| `gstack-pair-agent`（内置名 `pair-agent`） | 配置 | 配对远程 AI 代理 | 一键生成 setup key，远程 agent（OpenClaw/Hermes/Codex）可连你的浏览器（详见浏览器分类） | "配对 agent" |
| `gstack-plan-ceo-review`（内置名 `plan-ceo-review`） | 配置 | CEO 模式计划审查 | 创始人视角：重想问题、找 10 星产品、挑战前提、四模式（扩张/精选/锁定/缩减） | "想大一点"、"战略审查" |
| `gstack-plan-design-review`（内置名 `plan-design-review`） | 配置 | 设计计划审查 | 交互式：每个设计维度 0-10 分并说明如何到 10，然后修计划 | "设计计划审查" |
| `gstack-plan-devex-review`（内置名 `plan-devex-review`） | 配置 | DX 计划审查 | 探索开发者画像、竞品基准、魔法时刻、摩擦点；三模式 | "DX 计划审查" |
| `gstack-plan-eng-review`（内置名 `plan-eng-review`） | 配置 | 工程计划审查 | 工程经理视角：架构、数据流、图、边界情况、测试覆盖、性能 | "工程审查"、"锁计划" |
| `gstack-plan-tune`（内置名 `plan-tune`） | 配置 | 计划调优 | 调整 AskUserQuestion 灵敏度与开发者心理画像；对话式，无 CLI 语法 | "调优问题"、"少问我" |
| `gstack-qa`（内置名 `qa`） | 配置 | QA 测试+修复 | 系统 QA 测试网站 → 原子修复 → 再验证；三档（快速/标准/详尽），输出健康评分 | "QA"、"test and fix" |
| `gstack-qa-only`（内置名 `qa-only`） | 配置 | 仅 QA 报告 | 系统测试并出结构化报告（健康分/截图/复现步骤），**不修任何东西** | "只报告"、"just report" |
| `gstack-retro`（内置名 `retro`） | 配置 | 周回顾 | 分析提交历史、工作模式、代码质量指标，团队感知、趋势跟踪 | "周回顾" |
| `gstack-review`（内置名 `review`） | 配置 | PR 着陆前审查 | 分析 diff：SQL 安全、LLM 信任边界、条件副作用、结构问题 | "PR 审查"、"pre-landing" |
| `gstack-setup-browser-cookies`（内置名 `setup-browser-cookies`） | 配置 | 导入浏览器 Cookie | 从真实 Chromium 导入 cookie 到无头会话（详见浏览器分类） | "导入 cookie" |
| `gstack-setup-deploy`（内置名 `setup-deploy`） | 配置 | 配置部署设置 | 检测部署平台（Fly/Render/Vercel/Netlify/Heroku/GA/自定义）、健康检查、状态命令，写入 CLAUDE.md | "配置部署" |
| `gstack-setup-gbrain`（内置名 `setup-gbrain`） | 配置 | 设置 gbrain | 安装 CLI、初始化本地 PGLite/Supabase brain、注册 MCP、每远程信任策略 | "设置 gbrain" |
| `gstack-ship`（内置名 `ship`） | 配置 | 发布工作流 | 检测+合并基线 → 测试 → 审查 diff → bump VERSION → 更新 CHANGELOG → 提交推送 → 创建 PR | "发布"、"ship it" |
| `gstack-skillify`（内置名 `skillify`） | 配置 | 固化抓取流为技能 | 最近成功的 /scrape 流程 → 永久浏览器技能（script+test+fixture），临时目录测试后询问再提交 | "固化流程" |
| `gstack-spec`（内置名 `spec`） | 配置 | 意图转规格 | 五阶段把模糊意图变成可执行 spec，归档 issue，可选 spawn 工作树 agent | "写 spec"、"file issue" |
| `gstack-sync-gbrain`（内置名 `sync-gbrain`） | 配置 | 同步 gbrain | 让 gbrain 与仓库代码同步并刷新 CLAUDE.md 搜索指引；幂等可重跑 | "同步 gbrain"、"re-index" |
| `gstack-unfreeze`（内置名 `unfreeze`） | 配置 | 解除冻结 | 解除 /freeze 的编辑限制（详见安全分类） | "解冻" |
| `gstack-upgrade` | 内置·配置 | 升级 gstack | 检测全局/内嵌安装，运行升级并展示新内容 | "升级 gstack" |

### Doko 技能（来源：配置）

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `doko-research` | 配置 | 迭代式网络研究 | 多轮搜索 → 交叉引用 → 结构化研究报告，带来源追踪 | "深度研究" |
| `doko-search` | 配置 | 免费网络搜索 | 通过真实 Chrome 读取搜索引擎结果页（Google/Bing/DDG/百度），无 API key 无费用无限流 | "搜索"、"查一下" |
| `doko-summarize` | 配置 | 网页摘要 | 真实 Chrome 读任意页面，产出可调详细度的结构化要点摘要 | "总结网页" |
| `doko-translate` | 配置 | 网页翻译 | 保留结构（标题/列表/表格/代码块）的逐节网页翻译 | "翻译网页" |
| `dokobot` | 配置 | Chrome 浏览器网页读取 | 读取任何网页（含 SPA/JS 渲染页）：提取内容、搜索、复杂动态页面 | "读网页"、"页面读不出来" |

### arkcli 系列（来源：内置·用户·配置）

火山引擎方舟命令行工具技能，覆盖模型/部署/用量/账单/精调全生命周期：

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `arkcli-shared` | arkcli 共享执行协议 | 首次配置入口、认证闸门、命令路由与选择顺序、输出/安全/二次确认；深度细节按需加载 references/ | 第一次用 arkcli、鉴权失败、判断产品命令 vs raw api |
| `arkcli-auth` | 认证管理 | 交互式登录、Volc SSO、状态查看、退出、生成 ARK API Key；CI 用 `init-volc` 无交互引导 | 登录、SSO、生成/重选 API Key |
| `arkcli-profile` | profile 切面管理 | 列出/新建/切换/删除/重命名 profile，管理 profile 内 API Key 与默认资源 | 多账号、切换配置 |
| `arkcli-config` | 本地配置管理 | profile 配置归因、update.mode 策略、config reset 与历史 yaml 排障 | 配置异常、reset |
| `arkcli-models` | 公共基础模型查询 | 列出/搜索/获取火山公共基础模型（doubao 等）详情；自定义模型走 arkcli-custommodel | "有哪些模型"、"模型详情" |
| `arkcli-custommodel` | 自定义模型仓库 | cm-* 模型从 TOS 导入、查询、量化模式；cm-* 相关边界判断必须用它 | "cm-xxx 模型" |
| `arkcli-deploy` | 创建推理接入点 | **新建 Endpoint 的唯一首选入口**：一键创建接入点并渲染多语言调用示例到 ./ark-examples/ | "部署模型"、"创建 endpoint" |
| `arkcli-infer-endpoint` | 接入点生命周期管理 | 已有 Endpoint 的获取/列表/启停/更新/删除；脚本化场景可用 raw create | "启停 endpoint"、"删 endpoint" |
| `arkcli-chat` | 快速对话/推理 | 数据面 Responses API：多模态、流式、多轮、临时 Key/Base URL/Endpoint、dry-run | "和模型对话"、"试效果" |
| `arkcli-understand` | 多模态专项理解 | 12 个理解配方：转写、抽取、字幕、定位等明确产出任务 | "转写"、"抽取字幕" |
| `arkcli-gen` | 图片/视频生成 | Ark 图片同步返回、视频异步轮询；支持临时 Key/Endpoint | "生成图片"、"生成视频" |
| `arkcli-code-example` | 生成调用示例代码 | 为基础模型生成 Python/Go/Java/Node/curl 示例并写入本地文件 | "要调用示例" |
| `arkcli-onboard` | 端到端接入向导 | 从"我想用某模型"到可调用 Endpoint（+ 可选示例代码）的引导流程 | "把模型接入我的应用" |
| `arkcli-api-explorer` | Raw API 探索 | 产品命令未覆盖时调用已注册 Action 的兜底能力 | "命令覆盖不了" |
| `arkcli-resources` | 实时资源查询 | 列出 profile 可见资源与调用兼容性；把 ep-xxx 解析为权威模型与工作流 | "这个 endpoint 能干嘛" |
| `arkcli-usage` | 用量查询 | usage stats（token/请求，5-30 分钟延迟）、套餐额度快照、余额、席位用量 | "用了多少"、"还剩多少额度" |
| `arkcli-billing` | 拆分账单查询 | 按账期/Endpoint/API Key/产品编码维度查结算金额；T+1 财务口径 | "花了多少钱"、"对账" |
| `arkcli-pricing` | 模型单价查询 | 基础模型结算单价（含账号折扣）与 AgentPlan/CodingPlan 套餐价 | "模型多少钱" |
| `arkcli-plans` | 套餐/席位管理 | 购买/续费/模型清单/APIKey 轮换 + 企业版席位全操作（列席位/分配/解绑） | "买套餐"、"分配席位" |
| `arkcli-doctor` | 统一排障入口 | CLI 健康、错误码、慢/超时/限流、DNS/TCP/TLS、媒体来源特征验证（verify-origin 需确认费用） | "arkcli 坏了"、"这个图是 Ark 生成的吗" |
| `arkcli-train-finetune` | 精调任务管理 | mcj-* 任务创建/查询/日志/导出为 custom model、衔接部署 | "精调模型" |
| `arkcli-agent` | Managed Agents 管理 | Agent/Skill/Env/Session/File/Memory Store/Vault/MCP OAuth 全生命周期 | "管理 agent" |
| `arkcli-connect` | 技能同步到 AI Agent | 把 arkcli 内置 skills 安装到本机 Claude Code/Codex 等 agent | "装到我的 agent" |
| `arkcli-helper` | 配置模型/provider | 为 Claude Code/Codex/OpenCode/OpenClaw/Trae 配置方舟 model/provider 与 Harness 工具 | "配置模型" |

### CLI-Anything 系列（来源：内置·用户）

为 70+ 款 GUI/桌面软件构建的 Agent 原生 CLI harness：

| 技能名 | 目标软件 | 用途 |
|--------|---------|------|
| `cli-anything` | 通用 | 核心方法论：为任何 GUI 应用/源码仓库构建、精炼、测试 CLI harness（Codex 版） |
| `cli-anything-adguardhome` | AdGuard Home | 网络广告过滤与 DNS 管理（REST API） |
| `cli-anything-anygen` | AnyGen | OpenAPI 生成幻灯片/文档/网页 |
| `cli-anything-audacity` | Audacity | 音频编辑 |
| `cli-anything-blender` | Blender | 3D 场景编辑 |
| `cli-anything-browser` | 浏览器 | DOMShell MCP：把 Chrome 无障碍树映射为虚拟文件系统，agent 原生导航 |
| `cli-anything-calibre` | Calibre | 电子书库管理、元数据编辑、格式转换 |
| `cli-anything-ccswitch` | CC Switch | 管理 AI 编码工具配置 |
| `cli-anything-chromadb` | ChromaDB | 向量数据库集合/文档/语义搜索管理（HTTP API v2） |
| `cli-anything-cloudanalyzer` | CloudAnalyzer | 点云评估与 QA：27 命令 8 组（评估/轨迹/分割/质量门/基线/处理/可视化/REPL） |
| `cli-anything-cloudcompare` | CloudCompare | 3D 点云与网格处理：41 命令（采样/过滤/网格/ICP/变换/导出） |
| `cli-anything-comfyui` | ComfyUI | AI 图像生成工作流队列与管理 |
| `cli-anything-dify-workflow` | Dify | 工作流 DSL CLI：创建/检查/验证/编辑/导出 |
| `cli-anything-drawio` | Draw.io | 图表创建与编辑（.drawio 文件） |
| `cli-anything-eez-studio` | EEZ Studio | LVGL 嵌入式 UI 编辑、项目检查、真后端构建 |
| `cli-anything-eth2-quickstart` | Ethereum | 自动化部署硬化以太坊节点（执行+共识客户端、RPC、健康检查） |
| `cli-anything-exa` | Exa | 网页搜索与内容检索工作流 |
| `cli-anything-firefly-iii` | Firefly III | 个人财务管理 |
| `cli-anything-freecad` | FreeCAD | 参数化 3D CAD：258 命令覆盖全部工作台（Part/Sketcher/PartDesign/TechDraw/FEM 等） |
| `cli-anything-gimp` | GIMP | 图像编辑（Pillow 后端） |
| `cli-anything-godot` | Godot | 游戏引擎项目管理、场景、导出、脚本执行 |
| `cli-anything-hermes` | Hermes Agent | 为 Hermes 构建 CLI-Anything harness |
| `cli-anything-inkscape` | Inkscape | 矢量图形编辑 |
| `cli-anything-intelwatch` | IntelWatch | 竞争情报、M&A 尽调、OSINT（Node.js/npx） |
| `cli-anything-iterm2` | iTerm2 | 终端会话控制：发文本、读输出、窗口管理、tmux -CC、偏好设置 |
| `cli-anything-iterm2-ctl` | iTerm2 高级 | 同上（独立封装） |
| `cli-anything-joplin` | Joplin | 笔记管理（真实 joplin 终端后端） |
| `cli-anything-jumpserver` | JumpServer | 堡垒机：资产/用户/权限/账号/会话/审计/运维 |
| `cli-anything-kdenlive` | Kdenlive | 视频编辑 |
| `cli-anything-krita` | Krita | 数字绘画：项目/图层/滤镜/导出、批处理 |
| `cli-anything-libreoffice` | LibreOffice | 文档编辑（真实 ODF 文件） |
| `cli-anything-live2d` | Live2D | Cubism 模型检查/校验/编辑/部署（.model3.json） |
| `cli-anything-lldb` | LLDB | 有状态 LLDB 调试（Python API） |
| `cli-anything-macrocli` | MacroCLI | GUI 宏：定义/列出/检查/执行参数化宏 |
| `cli-anything-mailchimp` | Mailchimp | 营销 API v3.0：303 命令 30 资源组 |
| `cli-anything-mermaid` | Mermaid | 图表创建/编辑/渲染（mermaid.ink 渲染） |
| `cli-anything-minimax` | MiniMax AI | 聊天（MiniMax-M3/M2.7）与 speech-2.x TTS |
| `cli-anything-mubu` | Mubu | 实时乐谱桥接 |
| `cli-anything-musescore` | MuseScore | 乐谱：转调、PDF/音频/MIDI 导出、分谱管理 |
| `cli-anything-n8n` | n8n | 工作流自动化：工作流/执行/凭证/变量/标签（Public API v1.1.1） |
| `cli-anything-notebooklm` | NotebookLM | 列表笔记本、管理来源、提问、生成工件（实验性） |
| `cli-anything-novita` | Novita AI | OpenAI 兼容客户端：DeepSeek/GLM 等模型 |
| `cli-anything-nsight-graphics` | Nsight Graphics | Windows GPU 捕获与 Trace Summary 分析 |
| `cli-anything-nslogger` | NSLogger | 日志文件解析/过滤/导出/监控 |
| `cli-anything-obs-studio` | OBS Studio | 场景集合编辑（JSON） |
| `cli-anything-obsidian` | Obsidian | 知识库：笔记管理、搜索、Local REST API |
| `cli-anything-ollama` | Ollama | 本地 LLM：模型管理、推理、嵌入 |
| `cli-anything-openrefine` | OpenRefine | 导入脏数据、JSON 操作历史、行检查、导出 |
| `cli-anything-openscreen` | Openscreen | 录屏编辑：缩放/变速/修剪/裁剪/注释（JSON 项目格式 + ffmpeg） |
| `cli-anything-pm2` | PM2 | Node.js 进程管理：列表/启动/停止/重启/日志 |
| `cli-anything-qgis` | QGIS | 项目/可写图层/要素/布局/导出 + qgis_process（真实 QGIS 运行时） |
| `cli-anything-quietshrink` | macOS | Apple Silicon HEVC 硬件编码压缩录屏（70-90% 体积，零 CPU 压力） |
| `cli-anything-rekordbox` | Rekordbox | DJ 库与现场混音：SQLCipher 库访问 + 虚拟 MIDI |
| `cli-anything-renderdoc` | RenderDoc | 图形调试捕获分析 |
| `cli-anything-rms` | Teltonika RMS | 设备管理与监控 |
| `cli-anything-safari` | Safari | 真实 Safari 浏览器自动化（safari-mcp，84 个工具 1:1 暴露） |
| `cli-anything-sbox` | s&box | Source 2 游戏引擎：项目管理、场景/材质/本地化、C# 生成、资产图查询 |
| `cli-anything-seaclip` | SeaClip-Lite | 项目管理板：问题/管道/代理/调度/活动 |
| `cli-anything-shotcut` | Shotcut | 视频编辑（MLT XML 格式） |
| `cli-anything-siyuan` | 思源笔记 | 笔记本/文档/块管理、知识库搜索 |
| `cli-anything-slay-the-spire-ii` | Slay the Spire 2 | 真实游戏控制：本地桥接 mod HTTP API，战斗/导航/奖励 |
| `cli-anything-threemf` | 3MF | 网格几何编辑：圆柱孔检测/修复、3D 打印文件对比 |
| `cli-anything-tigris` | Tigris | 对象存储：桶/对象/预签名 URL/快照/IAM（S3 兼容零出口费） |
| `cli-anything-unimol-tools` | Uni-Mol | 分子性质预测训练与推理工作流 |
| `cli-anything-unrealinsights` | Unreal Engine | 捕获/检查 Trace Store、导出时序与计数器摘要 |
| `cli-anything-videocaptioner` | VideoCaptioner | AI 视频字幕：转写、翻译、烧录（免费 ASR） |
| `cli-anything-wavetone` | WaveTone | 2.61 工作流控制（JSON manifest + 真实可执行） |
| `cli-anything-web-yu-pri` | Japan Post | 日本邮政 Web 服务：驱动真实浏览器登录/检查/截图/dry-run/填表 |
| `cli-anything-wiremock` | WireMock | HTTP mock 服务器管理 |
| `cli-anything-zoom` | Zoom | 会议管理、参与者、录制 |
| `cli-anything-zotero` | Zotero | 文献管理 |
| `cli-hub-meta-skill` | CLI Hub | 发现 Agent 原生 CLI：访问实时目录查找专业软件工具 |
| `opencli-usage` | OpenCLI | 顶层地图：适配器发现、通用 flags、输出格式、下一步加载哪个技能 |

### Nature 系列（来源：内置·用户）

| 技能名 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|-----------|---------|-----------|
| `nature-writing` | Nature 风格稿件撰写 | 起草/重构/规划 Nature 风格章节与首投材料：摘要、引言、方法、结果、讨论、标题、cover letter、推荐审稿人 | "写论文"、"投稿材料" |
| `nature-polishing` | 学术散文润色 | 润色/重构/翻译为简洁 Nature 风格英文，保留事实与引用意图；排版修复 | "润色论文"、"SCI 写作" |
| `nature-figure` | 论文配图 | 提交级科学图表（matplotlib/seaborn 或 R）：先定结论/证据逻辑/模板再画；可走 GPT-Image 图形摘要 | "画图"、"论文配图" |
| `nature-citation` | Nature/CNS 引用 | 把长段落切成可引用片段，只检索 Nature 系/AAAS/Cell 系期刊，输出引用管理文件 | "加引用"、"分段引用" |
| `nature-academic-search` | 多源文献检索 | PubMed/CrossRef/arXiv/Scopus 等协调检索、他引审计、引用指标表、文件格式转换 | "查文献"、"他引核实" |
| `nature-literature-pipeline` | 文献发现管道 | 自动化多源搜索 → 六维评分 → 精读 → 格式化交付 → 归档；支持 cron + 飞书/Telegram | "文献综述"、"自动文献" |
| `nature-downloader` | 学术全文获取 | 合法全文：机构访问、CNKI、OA 检索、出版社 API、浏览器回退 | "下载论文" |
| `nature-reader` | 论文中英对照阅读 | 全文双栏对照 Markdown：图表/公式感知、源文锚点保留、逐块翻译 | "读论文"、"翻译论文" |
| `nature-paper2ppt` | 论文转 PPT | 从论文生成 Nature 风格中文 PPTX：证据链叙事、选图、演讲者备注、防溢出 QA | "论文做 PPT"、"组会汇报" |
| `nature-reviewer` | 模拟审稿人 | Nature 风格预提交同行评审：重大/次要问题、阻断标志；多审稿人互相隔离 | "预审"、"模拟审稿" |
| `nature-response` | 审稿意见回复 | point-by-point 回复信、rebuttal、cover letter、标红修订稿；审稿人隔离、防文章膨胀 | "回复审稿人" |
| `nature-paper-to-patent` | 论文转专利 | 生成证据支撑的中文发明专利草稿与技术交底书：权利要求-证据映射、Office Math 公式 | "转专利"、"交底书" |
| `nature-data` | 数据可用性声明 | Nature 风格 Data Availability 声明、仓库选择、数据集引用、FAIR 清单 | "数据声明" |
| `nature-experiment-log` | 实验日志 | 标准化实验日志（图片/语音/文字输入 → YAML frontmatter Markdown），可选飞书/Obsidian 集成 | "实验日志" |

### 独立技能

| 技能名 | 来源 | 一句话说明 | 详细描述 | 什么时候用 |
|--------|------|-----------|---------|-----------|
| `notion-mcp` | 用户·配置 | Notion MCP 工具 | 创建/搜索/更新页面、数据库、视图、评论；把有用的对话沉淀为 Notion 记忆 | "Notion 操作" |
| `reverse-engineering` | 内置·配置 | macOS 私有 API 逆向 | Ghidra 静态定位、LLDB 动态验证、字节 Pattern 提取、ADRP/BL 解码、Swift/ObjC ABI 推断、崩溃根因 | "逆向 macOS"、"分析 Mach-O" |
| `sisyphus-execution-rules` | 配置 | Sisyphus 执行规则 | 会话开始强制加载：Agent 分工、实时更新、Git 限制、资源管理 | 会话开始 |
| `test-auth-bootstrap` | 配置 | 测试环境认证引导 | 自动获取 auth token（注册/登录/验证码/邮箱验证），E2E 验证无需人工凭证 | "测试被登录挡住" |

---

## 高频组合模式

### 功能开发流程
```
think → planning-and-task-breakdown → implement → tdd → check → create-pull-request
```

### Bug 修复流程
```
hunt → diagnosing-bugs → tdd → check → create-pull-request
```

### 代码审查流程
```
check → code-review → code-review-and-quality
```

### UI 开发流程
```
design-taste-frontend → frontend-ui-engineering → ui → webapp-testing → gstack-qa
```

### 学术写作流程
```
nature-academic-search → nature-writing → nature-polishing → nature-figure → nature-paper2ppt
```

### 文档处理流程
```
write → docx/pptx/xlsx → pdf → markitdown
```

### 网络研究流程
```
doko-search → dokobot → doko-research → exa-search → paper-lookup
```

### CLI 工具开发
```
cli-anything → cli-hub-meta-skill → opencli-usage
```

### 安全审查
```
security-and-hardening → gstack-cso → gstack-careful
```

### 性能优化
```
web-performance-audit → performance-optimization → debug-optimize-lcp
```

### 单细胞分析流程
```
scanpy → anndata → scvi-tools → scvelo → pathway-enrichment
```

### 药物发现流程
```
rdkit → datamol → deepchem → pytdc → medchem → diffdock
```

### 蛋白质工程流程
```
esm → tamarind → diffdock → molecular-dynamics → glycoengineering
```

### 基因组学流程
```
biopython → pysam → bulk-rnaseq → pydeseq2 → pathway-enrichment
```

### 文献综述流程
```
paper-lookup → literature-review → citation-management → nature-citation
```

### 量子计算流程
```
qiskit / cirq / pennylane / qutip
```

### 地理空间流程
```
geopandas → geomaster → matplotlib → scientific-visualization
```

---

## 使用说明

1. **扫描此文件**: 在执行任务前快速扫描相关类别
2. **加载多个技能**: 可以同时加载多个相关技能（R25）
3. **流程技能优先**: `think`、`hunt`、`tdd` 等过程技能优先于实现技能
4. **用户指令优先**: 用户指令 > 技能 > 默认行为
5. **多来源去重**: 同一技能多来源时只加载一次，来源列已标注全部位置

---

## 技能来源

| 来源 | 说明 | 优先级 |
|------|------|--------|
| 项目级 | 项目特定技能（`.agents/skills/`）——**本仓库当前无项目级技能** | 最高 |
| 内置 | omp（oh-my-pi）内置注册表，397 个技能名，本会话可直接使用 | 高 |
| 用户 | 用户安装技能（`~/.agents/skills/`），371 个目录 | 高 |
| 配置 | opencode 配置技能（`~/.config/opencode/skills/`），88 个目录 | 中 |

**去重统计**（2026-08-26 实盘核对）：
- 磁盘唯一技能：**434**（371 + 88 − 25 同名重复）
- 同名双目录：25 个（`arkcli-*` 24 个 + `notion-mcp`）
- 注册表 ↔ 磁盘：347 个同名直映 + **50 个 GStack 别名**（注册表无前缀名 ↔ 磁盘 `gstack-*`）
- 磁盘独有（不在注册表）：87 个（`doko-*`、`ai-chat-browser`、`sisyphus-execution-rules`、`test-auth-bootstrap` 等）
- 注册表独有（磁盘以别名存在）：50 个 GStack 别名
- 别名合并：`browser` / `browser-use` / `browser-harness` 三目录内容相同，合并为一行
- **不收录**：opencode 宿主内置技能（playwright/frontend/git-master 等）——omp 无法调用
- **同名不同技能**：`learn` ≠ `gstack-learn`（六阶段研究 vs 经验管理）；`health` ≠ `gstack-health`（健康审计 vs 质量仪表盘）

---

*此文件由 AI 自动维护。当新增或修改技能时，应同步更新此索引。*
