# 记账本系统升级指南 — Docker 镜像 + CI/CD 方案

> 面向技术小白的完整流程讲解，每一步都解释了"是什么"和"为什么"。

---

## 一、核心概念先搞懂

### 1. Docker 是什么？

```
传统部署：                    Docker 部署：
                                     ┌─────────────────┐
  ┌──────────┐                      │   Docker 容器     │
  │ 服务器    │ 装 JDK 17            │  ┌─────────────┐ │
  │ 装了很多   │ 装 MySQL             │  │ 你的 JAR 包   │ │
  │ 乱七八糟   │ 装 Redis             │  │ + JDK 17     │ │
  │ 的东西     │ 装 Nginx             │  │ 打包在一起    │ │
  │ 互相干扰   │ 环境冲突...          │  └─────────────┘ │
  └──────────┘                      └─────────────────┘
```

**Docker 就像一个集装箱**——把你的程序 + 运行环境（JDK、配置）打包在一起，到任何服务器上都能直接运行，不用担心"你这台机器 JDK 版本不对"之类的问题。

| 术语 | 大白话解释 |
|------|-----------|
| **镜像（Image）** | 一个只读的模板，相当于"集装箱的图纸"。比如 `eclipse-temurin:17-jre` 镜像 = 装了 JDK 17 的 Linux 系统 |
| **容器（Container）** | 从镜像启动的运行实例，相当于"根据图纸造出来的集装箱"。可以启动、停止、删除、重建 |
| **Dockerfile** | 一个文本文件，描述怎么造你的镜像。相当于"集装箱的制造说明书" |
| **docker-compose** | 同时管理多个容器的工具，比如你的系统需要：后端容器 + MySQL 容器 + Nginx 容器 |

### 2. CI/CD 是什么？

> **关键认知：CI 和 CD 是两件独立的事情，不是每次都一起跑。**

```
传统流程（手动）：
  写代码 → 手动打包 → 手动上传 → 手动部署 → 手动重启 → 祈祷别出错

正确理解 CI/CD：
  ┌─────────────────────────────────────────────────────┐
  │  CI（自动，每次 push 都跑）                           │
  │  git push → 编译 → 测试 → 结果通知                    │
  │                                                     │
  │  CD（手动触发，确认无误后才跑）                        │
  │  开发者点按钮 → 打包 → 构建镜像 → 部署到服务器          │
  └─────────────────────────────────────────────────────┘
```

| 术语 | 大白话解释 |
|------|-----------|
| **CI（持续集成）** | **自动的**。每次 push 代码，自动帮你编译、测试，第一时间告诉你有没有 bug。**只管检查，不管部署。** |
| **CD（持续交付）** | **手动的**。功能开发完、测试通过了，开发者确认无误后手动触发，才构建镜像并部署到服务器。注意：准确叫「持续交付 (Continuous Delivery)」，因为不是全自动部署到生产环境。 |
| **GitHub Actions** | GitHub 官方的 CI/CD 工具，免费，在你的仓库里写个配置文件就能用 |
| **Workflow（工作流）** | 你写的 CI/CD 配置文件，定义执行什么操作 |
| **Runner（运行器）** | GitHub 提供的一台临时虚拟机，免费帮你跑 Workflow |
| **workflow_dispatch** | GitHub Actions 的关键机制，让 Workflow 可以被**手动触发**（而不是自动） |

**为什么 CI 和 CD 要分开？**

```
如果每次 push 都自动部署：
  ❌ 开发到一半的代码被部署到线上 → 用户看到半成品
  ❌ 一天 push 几十次 → 构建几十个没用的镜像 → 浪费资源和时间
  ❌ 有人不小心 push 了 bug 代码 → 线上直接炸

正确的方式（CI 自动 + CD 手动）：
  ✅ push 100 次 → CI 跑 100 次（编译 + 测试，不构建镜像）
  ✅ 功能开发完 → 手动触发 1 次 CD → 构建 1 个镜像 → 部署
  ✅ 开发者确认无误才上线 → 安全可控
```

**如何手动触发 CD？**

在 GitHub 仓库页面直接操作：

```
仓库页面 → Actions 标签 → 左边选"CD 部署" → 右边点 Run workflow →
输入版本号（如 v1.2.0）→ 点绿色 Run workflow 按钮 → 开始部署
```

不需要命令行，完全在网页上点两下就好。

### 3. 镜像仓库是什么？

```
┌─────────────┐    push 上传    ┌──────────────┐    pull 下载    ┌──────────┐
│  GitHub      │ ─────────────→ │  镜像仓库      │ ─────────────→ │  服务器    │
│  Actions     │                │  (存镜像的)    │                │  (部署的)  │
└─────────────┘                └──────────────┘                └──────────┘
```

镜像仓库就是"存 Docker 镜像"的地方，相当于 docker 界的 GitHub。

| 仓库 | 说明 |
|------|------|
| **Docker Hub** (`docker.io`) | 官方公共仓库，免费版可以存 1 个私有镜像，公开镜像无限 |
| **GitHub Container Registry** (`ghcr.io`) | GitHub 自家的，跟你的仓库无缝集成，免费 |
| **阿里云容器镜像服务** | 国内速度快，免费，推荐国内使用 |

---

## 二、完整流程图解

### 日常开发：每次 push 自动跑 CI（编译 + 测试）

```
你（开发者）                    GitHub
     │                           │
     │  git push                 │
     │ ────────────────────────→ │
     │                           │  ② CI Workflow 自动触发
     │                           │     ├─ 拉取代码
     │                           │     ├─ 安装 JDK 17
     │                           │     ├─ mvn compile（编译）
     │                           │     └─ mvn test（跑测试）
     │                           │
     │  ③ 结果反馈               │
     │ ←──────────────────────── │
     │                           │
     │  ✅ 通过 → 继续开发        │
     │  ❌ 失败 → 修复 bug        │
     │                           │
```

**不管功能有没有做完，只管代码能不能编译通过、测试过不过。没有构建镜像，没有部署。**

---

### 发布上线：手动触发 CD（构建镜像 + 部署）

```
你（开发者）                    GitHub                     服务器
     │                           │                          │
     │  ① 功能开发完毕            │                          │
     │  ② 在 GitHub 网页上手动触发 CD                         │
     │ ────────────────────────→ │                          │
     │                           │                          │
     │                           │  ③ CD Workflow 开始执行   │
     │                           │     ├─ 拉取代码            │
     │                           │     ├─ mvn package（打包） │
     │                           │     ├─ docker build（构建镜像）
     │                           │     ├─ docker push（推送到镜像仓库）
     │                           │     │                      │
     │                           │     │  ④ 镜像推到仓库       │
     │                           │     └─────────────────────→│ 镜像仓库(ghcr.io)
     │                           │                            │
     │                           │  ⑤ SSH 登录服务器执行       │
     │                           │     ├─ docker pull 新镜像   │
     │                           │     ├─ 停掉旧容器           │
     │                           │     ├─ 启动新容器           │
     │                           │     └─ 清理旧镜像           │
     │                           │                            │
     │  ⑥ 用户访问新版本          │                            │
     │ ←─────────────────────────────────────────────────────│
```

**功能开发完、测试通过、确认无误后，手动点一下按钮才开始部署。每次发布只构建一个镜像。**

---

## 三、每一步的详细讲解

### 场景一：日常开发（CI 自动流程）

#### 第一步：写完代码，git push

```bash
# 在你的电脑上
cd d:/itcast/vibeCoding/vibeCoding_HeiMa/account_book
git add .
git commit -m "feat: 完成了某个功能"
git push origin snake-eat
```

#### 第二步：GitHub Actions 自动跑 CI

你 push 之后，GitHub 检测到分支有变动，自动启动 **CI Workflow**。

**CI Workflow 做什么**：

```
# 文件：.github/workflows/ci.yml
# 任何分支 push 都自动触发

1. 签出代码       → 把最新代码下载到 GitHub 的虚拟机里
2. 设置 JDK 17    → 装上 Java 17 环境
3. Maven 编译     → 运行 mvn compile，检查代码能不能编译通过
4. 跑测试         → 运行 mvn test，检查有没有 bug
```

**没有打包，没有构建镜像，没有部署。** 纯粹检查代码质量。

#### 第三步：看结果

```
GitHub 仓库页面 → Actions 标签 → 看到 CI Workflow 运行结果

✅ 绿色 → 代码没问题，继续开发
❌ 红色 → 编译失败或测试挂了，点进去看日志，修复后重新 push
```

---

### 场景二：发布上线（CD 手动流程）

#### 第一步：确认可以发布

- 功能全部开发完
- CI 全部绿色（编译通过、测试通过）
- 自己本地也跑了一遍，确认没问题
- **可以上线了！**

#### 第二步：手动触发的 CD Workflow（配置文件怎么写）

```yaml
# 文件：.github/workflows/deploy.yml

name: CD 部署

on:
  workflow_dispatch:      # ← 关键！只接受手动触发，不会自动跑
    inputs:
      version:
        description: "要部署的版本号（如 v1.2.0）"
        required: true
        default: "v1.0.0"

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: 设置 JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: "17"
          distribution: "temurin"

      - name: 打包
        run: cd backend && mvn package -DskipTests

      - name: 登录镜像仓库
        run: echo "${{ secrets.GHCR_TOKEN }}" | docker login ghcr.io -u ${{ secrets.GHCR_USERNAME }} --password-stdin

      - name: 构建并推送 Docker 镜像
        run: |
          docker build -t ghcr.io/你的用户名/account-book:${{ github.event.inputs.version }} -f backend/Dockerfile backend/
          docker tag ghcr.io/你的用户名/account-book:${{ github.event.inputs.version }} ghcr.io/你的用户名/account-book:latest
          docker push ghcr.io/你的用户名/account-book:${{ github.event.inputs.version }}
          docker push ghcr.io/你的用户名/account-book:latest

      - name: SSH 到服务器部署
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            cd /opt/account_book
            docker pull ghcr.io/你的用户名/account-book:${{ github.event.inputs.version }}
            docker-compose up -d backend
            # 只清理本项目旧版本镜像，不影响其他项目
            docker images ghcr.io/你的用户名/account-book --filter "before=ghcr.io/你的用户名/account-book:${{ github.event.inputs.version }}" -q | xargs -r docker rmi || true
```

**关键机制 `workflow_dispatch`**：

```
普通的 on: push → 每次 push 自动跑
on: workflow_dispatch → 只有你在网页上手动点"Run workflow"才会跑
```

#### 第三步：手动触发部署

在你自己的 GitHub 仓库页面上操作：

```
① 打开 https://github.com/你的用户名/account_book
② 点顶部 Actions 标签
③ 左边列表点"CD 部署"
④ 右边点"Run workflow"下拉按钮
⑤ 在下拉框里输入版本号，比如 v1.2.0
⑥ 点绿色"Run workflow"按钮
```

```
┌───────────────────────────────────────────────┐
│  Actions / CD 部署                              │
│                                                 │
│  This workflow has a workflow_dispatch event    │
│  trigger.                                       │
│                                                 │
│  ┌──────────────────────────────────┐          │
│  │ Use workflow from               │          │
│  │ Branch: [snake-eat       ▾]     │          │
│  │                                  │          │
│  │ 要部署的版本号                     │          │
│  │ [v1.2.0               ]          │ ← 输入版本号
│  │                                  │          │
│  │         [Run workflow]           │ ← 点这个按钮
│  └──────────────────────────────────┘          │
└───────────────────────────────────────────────┘
```

之后 GitHub Actions 开始自动执行：打包 → 构建镜像 → 推送到仓库 → 部署到服务器。你什么都不用管。

---

### Dockerfile 详解（镜像制造说明书）

```dockerfile
# 后端 Dockerfile 示例（放在 backend/ 目录下）

# FROM：基于哪个基础镜像。JDK 17 的 Linux 系统，别人已经做好了，直接用
FROM eclipse-temurin:17-jre

# WORKDIR：容器里的工作目录，相当于 cd /app
WORKDIR /app

# COPY：把构建好的 JAR 包复制到镜像里
COPY target/app.jar app.jar

# EXPOSE：告诉别人这个容器会监听 8080 端口
EXPOSE 8080

# ENTRYPOINT：容器启动时执行的命令，就是 java -jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**大白话**：这个 Dockerfile 说的是——"拿一个装了 JDK 17 的 Linux，把 app.jar 放进去，启动时运行它。"

**构建命令**（GitHub Actions 自动执行的）：
```bash
docker build -t ghcr.io/你的用户名/account-book:v1.2.3 .
#           ↑                   ↑                        ↑
#      构建镜像           镜像名称:版本号              当前目录
```

---

### 镜像怎么到服务器上？

```
GitHub Actions 虚拟机                   镜像仓库                       你的服务器
    │                                      │                              │
    │  docker build 构建镜像                │                              │
    │  ┌──────────────┐                    │                              │
    │  │ myapp:v1.2.3 │ ── docker push ──→ │  💾 存起来了                  │
    │  └──────────────┘                    │                              │
    │                                      │                              │
    │                                      │  docker pull myapp:v1.2.3    │
    │                                      │ ←─────────────────────────── │
    │                                      │                              │
    │                                      │    ┌──────────────┐          │
    │                                      │    │ myapp:v1.2.3 │          │
    │                                      │    └──────────────┘          │
```

1. GitHub Actions 用 `docker push` 把镜像推到仓库（存在云端）
2. 服务器用 `docker pull` 从仓库拉取镜像（下载到本地）
3. 服务器停止旧容器，用新镜像启动新容器

---

### 服务器端的 docker-compose

服务器上有个 `docker-compose.yml`，描述了你的系统包含哪些服务：

```yaml
# docker-compose.yml（放在服务器上）
version: "3.8"

services:
  # MySQL 数据库
  db:
    image: mysql:8.0                # 用官方 MySQL 镜像
    restart: unless-stopped         # 服务器重启后自动启动
    environment:
      MYSQL_ROOT_PASSWORD: 你的密码
      MYSQL_DATABASE: account_book
    volumes:
      - mysql_data:/var/lib/mysql   # 数据持久化，容器删了数据还在
    ports:
      - "3306:3306"

  # 你的后端
  backend:
    image: ghcr.io/你的用户名/account-book:latest  # 拉最新版本
    restart: unless-stopped         # 服务器重启后自动启动
    depends_on:
      - db                          # 等 MySQL 启动后再启动
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/account_book
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: 你的密码
    ports:
      - "8080:8080"

volumes:
  mysql_data:                       # 数据卷，保证数据不丢失
```

**升级时只改 backend 容器**：

```bash
# 服务器端执行
docker pull ghcr.io/你的用户名/account-book:latest   # 拉新镜像
docker-compose up -d backend                         # 只重建 backend 容器
```

数据库容器不受影响，数据完好无损。

---

## 四、一键回滚怎么做到的？

因为**每次构建的镜像都带版本号存着**：

```
镜像仓库里存着：
  ghcr.io/你的用户名/account-book:v1.0.0  ← 旧版本
  ghcr.io/你的用户名/account-book:v1.1.0  ← 旧版本
  ghcr.io/你的用户名/account-book:v1.2.0  ← 当前版本
  ghcr.io/你的用户名/account-book:v1.2.1  ← 新版本（出问题了！）
```

**回滚 = 指定旧版本号重建容器**：

```bash
# 把 docker-compose.yml 里的版本从 v1.2.1 改成 v1.2.0
# vim docker-compose.yml  修改一行
# docker-compose up -d     重建容器
```

或者直接用 `docker-compose` 指定旧版本：

```bash
# 修改 docker-compose.yml 中 backend 的 image 版本号：
# image: ghcr.io/你的用户名/account-book:v1.2.0  ← 改成旧版本
docker-compose up -d backend   # 重建容器，网络配置保持不变
```

> ⚠️ **不要用 `docker run` 手动起容器**：`docker run` 创建的容器不会加入 docker-compose 的网络，
> 后端无法通过容器名 `db` 访问 MySQL，数据库连接会全部失败。

**10 秒钟回滚完成**，不像传统方式要重新打包、重新部署。

---

## 五、完整文件清单

你需要创建的文件：

| 文件 | 位置 | 作用 |
|------|------|------|
| `ci.yml` | `.github/workflows/` | **CI 工作流**：每次 push 自动编译 + 测试 |
| `deploy.yml` | `.github/workflows/` | **CD 工作流**：手动触发，构建镜像 + 部署到服务器 |
| `Dockerfile` | `backend/` 目录 | 描述怎么构建后端镜像 |
| `docker-compose.yml` | 服务器上 `/opt/account_book/` | 描述系统有哪些服务、怎么启动 |
| `.dockerignore` | `backend/` 目录 | 告诉 Docker 哪些文件不打包（类似 .gitignore） |

---

## 六、总结

### 你的日常操作

```
日常开发（每天都做）：
  ✅ 写代码
  ✅ git push
  → 自动跑 CI（编译 + 测试）
  → 看结果：绿了就继续，红了就修

发布上线（确认无误后才做）：
  ✅ 确认功能开发完、测试通过
  ✅ 打开 GitHub 仓库 → Actions → CD 部署 → Run workflow → 输版本号
  → 自动构建镜像 → 推送到仓库 → 部署到服务器

回滚（出问题时）：
  ✅ 改 docker-compose.yml 里的版本号为上一个版本
  ✅ docker-compose up -d
  → 10 秒回滚完成
```

### 一张图总结

```
       CI（自动）                      CD（手动）
  ┌─────────────────┐          ┌─────────────────────┐
  │ 每次 push 都跑    │          │ 确认无误后才手动触发  │
  │                  │          │                     │
  │ ① 编译           │          │ ① mvn package      │
  │ ② 测试           │          │ ② docker build     │
  │                  │          │ ③ docker push      │
  │ 不管功能有没有做完 │          │ ④ SSH 部署到服务器   │
  │ 只管代码有没有问题 │          │                     │
  └─────────────────┘          └─────────────────────┘
  ↑ 一天几十次，轻量            ↑ 几天一次，正式发布
```

**CI 是保镖，帮你揪错；CD 是快递员，你叫他他才发货。**

---

## 七、下一步

如果你打算开始实施这套方案，告诉我你用的是哪家云服务器（阿里云/腾讯云/自己的 VPS），我帮你把这些配置文件全部写好。
