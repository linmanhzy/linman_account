# 林蛮记账 · 全局记忆与经验

> 最后更新：2026-07-22。本文件只保存**经验与全局记忆**（环境、坑位、稳定约定）；项目当前进展/动态请见 **`PROJECT_CURRENT.md`**（仓库根目录）。下半部分（superpowers-zh 标记内）为 IDE 自动生成的技能指引，请勿改动标记内内容。

## 文档分工（必读）
- **CODEBUDDY.md（本文件）**：经验 + 全局记忆。例：本机环境密码、命令约定、踩坑记录、稳定架构决策。
- **PROJECT_CURRENT.md**：项目当前状况。例：近期进展、当前在做什么、下一步待办。每次操作都要更新它。
- 两个文件都在仓库根目录，都会在会话开始时自动加载。

## 自动更新规则（AI 必读 —— 每次操作必须执行）
> 说明：CodeBuddy 暂无「文件改动触发」的钩子，但 CODEBUDDY.md 与 PROJECT_CURRENT.md 都会在**每次会话开始时自动作为项目指引加载**。因此「自动更新」= 把规则固化进文件，让每个 AI 会话开工前先读到并遵守。

- **每次完成一次操作（改代码、修 bug、落地功能、切换任务）后，立即同时更新两个文件**，不要等一大段工作结束才补：
  - `CODEBUDDY.md`：若产生**新的经验/全局记忆**（新坑位、环境变化、稳定约定）→ 追加或更新。
  - `PROJECT_CURRENT.md`：把顶部「最后更新」改成当天，在「近期进展」**最顶部**追加一条带日期的进展（现象 / 根因 / 改动文件 / 验证结果，一句话讲清「改了什么、为什么、怎么验证」）。
- 小改动也记一笔，保证下次会话能直接拿到最新工作状态，不用重新排查。
- 只改两文件上半部分，不要动 CODEBUDDY.md 下方 `superpowers-zh` 标记内的内容。

## 项目概况
- 名称：林蛮记账（多用户联网记账系统）
- 技术栈：Spring Boot（`backend/`）+ React + Tauri v2（`frontend/`，同一套代码产出 Web 与 Android APK）
- 当前形态：Web 端 + 安卓 App（内测免费，不上 Google Play，不做 iOS）

## 近期进展

> 项目当前进展与近期动态已迁移至 **`PROJECT_CURRENT.md`**（仓库根目录），请在那里查看与更新；本文件只保留经验与全局记忆。

## 给后续对话的提醒
- 启动后端：优先在**本机 cmd**（非 codebuddy 终端）跑 `mvn spring-boot:run`，或直接运行 `backend/run-dev.bat`。
- 改了本机 MySQL root 密码，记得同步改 `run-dev.bat` 里那行密码。
- 前端命令（`npm install` / `npm run dev` / `build_android.bat`）都在 `frontend/` 目录执行；`.env` 放在 `frontend/` 下。
- 后端并发测试：JUnit 5 + MockMvc（profile `concurrency`，库 `conctestdb`）与 Locust（backend/tests/loadtest/）两套方案，均已落地。
- **前端单元测试**：vitest 2.1.0 + jsdom 已配置（`vitest.config.ts`），运行 `cd frontend && npx vitest run`（或 `npm test`）。`tsconfig.json` 已加 `"types": ["vitest/globals"]`。测试文件放在 `src/**/*.test.ts`。
- **Debug APK 构建**：`build_debug_server.bat` 参照 CD 方案用 `set VITE_API_BASE` 进程环境变量注入（Vite 最高优先级），不再仅依赖 `.env` 文件。支持 `set DEBUG_SERVER_URL=http://你的IP:8080` 覆盖目标地址。构建前校验 URL 格式（必须以 http:// 或 https:// 开头）。
- **API_BASE 解析**：`src/api/apiBase.ts` 的 `resolveApiBase()` 在 PROD 模式 + VITE_API_BASE 为空时抛出中文详细错误，`showFatalErrorOverlay()` 将错误注入 DOM 顶部红色覆盖层。纯函数可测试（`src/api/apiBase.test.ts`，14 例全绿）。

---

<!-- superpowers-zh:begin (do not edit between these markers) -->
# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.codebuddy/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文 review 沟通参考——话术模板、分级标注（必须修复/建议修改/仅供参考）、国内团队常见反模式应对。仅在用户显式 /chinese-code-review 时调用，不要根据上下文自动触发。
- **chinese-commit-conventions**: 中文 commit 与 changelog 配置参考——Conventional Commits 中文适配、commitlint/husky/commitizen 中文模板、conventional-changelog 中文配置。仅在用户显式 /chinese-commit-conventions 时调用，不要根据上下文自动触发。
- **chinese-documentation**: 中文文档排版参考——中英文空格、全半角标点、术语保留、链接格式、中文文案排版指北约定。仅在用户显式 /chinese-documentation 时调用，不要根据上下文自动触发。
- **chinese-git-workflow**: 国内 Git 平台配置参考——Gitee、Coding.net、极狐 GitLab、CNB 的 SSH/HTTPS/凭据/CI 接入差异与镜像同步配置。仅在用户显式 /chinese-git-workflow 时调用，不要根据上下文自动触发。
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发，或在执行实现计划之前使用——通过原生工具或 git worktree 回退机制确保隔离工作区存在
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，读取对应的 `.codebuddy/skills/<skill-name>/SKILL.md` 并严格遵循其流程。
<!-- superpowers-zh:end -->
