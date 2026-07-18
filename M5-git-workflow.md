# M5 贪吃蛇小游戏 — Git 分支开发与合并操作指南

> 适用场景：基于已完成的 M4，新建独立分支开发 M5 贪吃蛇小游戏，开发完成后再合并回 `main`。

## 当前状态（开始之前）

- 当前所在分支：`main`
- 工作区存在 **M4 未提交改动**（8 个修改文件 + 一批新增的 controller / dto / service / page / api 文件）
- 建议：先把 M4 收尾提交到 `main`，M5 才能基于"完整的 M4"干净地开发

---

## 第 0 步（强烈建议先做）：把 M4 提交到 main

```bash
cd d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book

# 暂存所有改动（含新增文件）
git add -A

# 提交 M4（message 可按你的习惯修改）
git commit -m "M4: 反馈/通知模块及权限守卫与性能修复"
```

> 如果你**不想现在提交 M4**，也可以用 `git stash` 暂存，等 M5 合并完再 `git stash pop` 处理。但推荐直接提交，历史更清晰。

---

## 第 1 步：从 main 创建并切换到 M5 开发分支

```bash
git switch -c feature/m5-snake
```

- 该命令基于当前 `main` 创建 `feature/m5-snake` 分支并切换过去。
- 分支命名也可改为 `m5/snake-game` 等，保持一致即可。

---

## 第 2 步：开发贪吃蛇小游戏（M5）

在 `feature/m5-snake` 分支上正常编码。

- 项目已有骨架 `src/pages/SnakeGame.tsx`，可在此基础上完善 M5 功能。
- 开发过程中可随时 `git add -A && git commit` 做阶段性提交。

---

## 第 3 步：在 M5 分支完成提交

```bash
git add -A
git commit -m "M5: 贪吃蛇小游戏"
```

---

## 第 4 步：开发完成后合并回 main

```bash
# 切回 main
git switch main

# 合并 M5 分支（--no-ff 保留一个合并节点，方便追溯里程碑）
git merge --no-ff feature/m5-snake
```

若 Git 提示冲突：

1. 手动解决冲突文件
2. 执行以下命令完成合并提交：

```bash
git add -A
git commit
```

---

## 第 5 步：推送到远程（GitHub）

```bash
# 推 main
git push -u origin main

# 如果也想把分支推上去供备份 / PR：
git push -u origin feature/m5-snake
```

> 注意：本机网络 **SSH(22 端口) 被封锁**，远程已统一为 HTTPS，推送时用户名填 `linmanhzy`，密码填 GitHub 个人访问令牌（PAT，不是账户密码）。

---

## 第 6 步（可选）：合并后清理本地分支

```bash
git branch -d feature/m5-snake
```

- 已合并的分支用 `-d` 删除是安全的。
- 若分支也已推到远程，删除远程分支：

```bash
git push origin --delete feature/m5-snake
```

---

## 流程速览

```
main (M4 已提交)
   │
   ├─ git switch -c feature/m5-snake   →  开发 M5
   │        │
   │        └─ git commit (M5 完成)
   │
   └─ git switch main
          │
          └─ git merge --no-ff feature/m5-snake   →  合并回主线
                 │
                 └─ git push -u origin main
```
