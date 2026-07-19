# 记账大王 · 完整部署指南

> 从零开始，到外部（网页 + 手机）能访问的完整步骤。
> 每一步都可直接复制粘贴执行。

---

## 目录

1. [登录服务器](#一登录服务器)
2. [安装 Docker](#二安装-docker)
3. [开放防火墙端口](#三开放防火墙端口)
4. [创建目录结构](#四创建目录结构)
5. [配置环境变量](#五配置环境变量)
6. [Nginx 说明](#六nginx-说明)
7. [上传部署文件](#七上传部署文件)
8. [首次启动](#八首次启动)
9. [验证部署](#九验证部署)
10. [日常运维命令](#十日常运维命令)
11. [常见问题](#十一常见问题)
12. [后续升级（CD 部署）](#十二后续升级cd-部署)
13. [APK 分发（Android）](#十三apk-分发android)
14. [性能基准](#十四性能基准)

---

## 一、登录服务器

打开你电脑的终端（Windows 用 PowerShell / CMD，Mac/Linux 用 Terminal），执行：

```bash
ssh 你的用户名@你的服务器IP
```

示例：
```bash
ssh root@123.45.67.89
```

输入密码后进入服务器，看到类似 `root@ubuntu:~#` 的提示符就成功了。

---

## 二、安装 Docker

Docker 是一个「打包运行环境」的工具，让程序在任何服务器上都能一致运行。

在服务器终端执行以下命令（逐条复制粘贴）：

```bash
# 1. 安装 Docker（一键脚本，约 1-2 分钟）
curl -fsSL https://get.docker.com | sudo sh

# 2. 让你的用户不用每次都输 sudo（可选但推荐）
sudo usermod -aG docker $USER

# 3. 立即生效上一步配置
newgrp docker

# 4. 验证安装成功（应输出版本号，如 Docker version 27.x.x）
docker --version

# 5. 同时安装 docker compose 插件（如果上面命令报错）
sudo apt install docker-compose-plugin -y
```

**验证**：如果 `docker --version` 输出了版本号，安装成功。

---

## 三、开放防火墙端口

你需要开放两个端口：

| 端口 | 用途 | 是否必须 |
|------|------|---------|
| 80 | 网页访问（HTTP） | ✅ 必须 |
| 8080 | 后端 API | ✅ 必须 |
| 443 | HTTPS | ❌ 暂不需要 |

```bash
# 开放端口
sudo ufw allow 80/tcp
sudo ufw allow 8080/tcp

# 查看防火墙状态（确认端口已开放）
sudo ufw status
```

> **云服务器用户**：除了服务器防火墙，还需要去阿里云/腾讯云等控制台的「安全组」中添加入站规则，放行 80 和 8080 端口。

---

## 四、创建目录结构

```bash
# 创建项目根目录
sudo mkdir -p /opt/account_book

# 进入目录
cd /opt/account_book

# 创建数据库数据目录
sudo mkdir -p mysql_data

# 设置当前用户为目录所有者（避免权限问题）
sudo chown -R $USER:$USER /opt/account_book
```

> **说明**：Nginx 和前端已经打包在 Docker 镜像里，服务器上不需要创建 `nginx/` 或 `frontend/` 目录。

---

## 五、配置环境变量

### 5.1 生成 JWT 密钥

```bash
# 执行这条命令，复制输出结果（后面要用）
openssl rand -base64 32
```

输出类似：`aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW=`

### 5.2 创建 .env 文件

```bash
nano /opt/account_book/.env
```

粘贴以下内容（**必须替换所有「请替换」开头的值**）：

```env
# ============================================================
# 记账大王 · 服务器环境变量
# ⚠️ 请勿将此文件提交到 Git！
# ============================================================

# 数据库 root 密码（自己设一个复杂密码，至少 8 位）
DB_PASSWORD=请替换为你的数据库密码_例如MyDb@2026

# JWT 密钥（用上面 openssl 命令生成的值替换）
JWT_SECRET=请替换为上面生成的JWT密钥

# 管理员初始密码（登录后台用）
ADMIN_PASSWORD=请替换为管理员密码_例如Admin@2026

# 前端允许访问的地址（用逗号分隔）
# Nginx 反代后浏览器发的是同源请求，无需 CORS；
# 这里配的是外部直接访问后端 8080 的场景（如 Swagger UI、手机 App）
CORS_ORIGINS=http://你的服务器IP,http://你的服务器IP:8080

# 镜像版本（首次用 latest，后续 CD 部署会自动更新）
APP_VERSION=latest
```

**示例**（假设服务器 IP 是 123.45.67.89）：

```env
DB_PASSWORD=Linman@2026Secure
JWT_SECRET=aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW=
ADMIN_PASSWORD=Admin@2026
CORS_ORIGINS=http://123.45.67.89,http://123.45.67.89:8080
APP_VERSION=latest
```

**保存文件**：按 `Ctrl+X` → 按 `Y` → 按 `Enter`

### 5.3 验证文件内容

```bash
cat /opt/account_book/.env
```

确认所有值都已替换，没有「请替换」字样。

---

## 六、Nginx 说明

Nginx 和前端已经打包在一个 Docker 镜像里（`account-book-frontend`），**无需在服务器上手动配置**。镜像内包含：

- 前端 React 构建产物（`dist/`）
- Nginx 配置文件（`nginx.conf`），已配置好：
  - 前端路由支持（React Router 回退到 `index.html`）
  - `/api/` 反向代理到后端 `backend:8080`
  - Swagger UI 代理
  - 静态资源 1 年强缓存

镜像由 Dockerfile 自动构建，配置在 `frontend/Dockerfile` 和 `frontend/nginx.conf` 中，如需修改可直接编辑这两个文件后重新构建。

---

## 七、上传部署文件

现在需要把本地项目的文件上传到服务器。**在你自己的电脑上执行**（不是在服务器上）。

### 7.1 上传 docker-compose.yml

打开**新的本地终端**（不要关闭服务器终端），进入项目目录：

```bash
cd D:\itcast\vibeCoding\vibeCoding_HeiMa\account_book

# 上传 docker-compose.yml 到服务器
scp docker-compose.yml root@你的服务器IP:/opt/account_book/
```

### 7.2 验证上传成功

回到服务器终端：

```bash
ls -la /opt/account_book/
```

应该能看到 `docker-compose.yml` 和 `.env` 文件。

---

## 八、首次启动

### 8.1 启动服务

```bash
cd /opt/account_book

# 拉取镜像并启动（首次约 3-5 分钟）
docker compose --env-file .env up -d
```

### 8.2 查看启动状态

```bash
# 查看所有容器状态
docker compose ps
```

应该看到三个容器都显示 `Up` 状态：

```
NAME                 STATUS              PORTS
account-book-db      Up (healthy)        0.0.0.0:3307->3306/tcp
account-book-backend Up                  0.0.0.0:8080->8080/tcp
account-book-nginx   Up                  0.0.0.0:80->80/tcp
```

> **注意**：如果状态是 `Restarting` 或 `Exited`，说明启动失败，查看下方「常见问题」。

### 8.3 查看启动日志

```bash
# 查看所有容器日志
docker compose logs

# 实时跟踪后端日志（按 Ctrl+C 退出）
docker compose logs -f backend
```

---

## 九、验证部署

### 9.1 浏览器访问前端

打开你电脑的浏览器，访问：

```
http://你的服务器IP
```

应该能看到「记账大王」的登录页面。

### 9.2 测试后端 API

访问：

```
http://你的服务器IP:8080/swagger-ui.html
```

应该能看到 API 文档页面，说明后端正常运行。

### 9.3 测试手机访问

用手机浏览器访问（确保手机和服务器在同一网络，或服务器有公网 IP）：

```
http://你的服务器IP
```

页面会自动适配手机屏幕。

### 9.4 注册测试账号

1. 在登录页面点击「注册」
2. 输入用户名和密码
3. 注册成功后自动登录

---

## 十、日常运维命令

### 查看容器状态

```bash
cd /opt/account_book
docker compose ps
```

### 查看日志

```bash
# 后端日志
docker compose logs backend

# 数据库日志
docker compose logs db

# 实时跟踪（按 Ctrl+C 退出）
docker compose logs -f
```

### 停止服务

```bash
cd /opt/account_book
docker compose --env-file .env down
```

### 启动服务

```bash
cd /opt/account_book
docker compose --env-file .env up -d
```

### 重启服务

```bash
cd /opt/account_book
docker compose --env-file .env restart
```

### 查看资源占用

```bash
docker stats --no-stream
```

### 数据库数据位置

MySQL 数据存储在 `/opt/account_book/mysql_data/` 目录，容器删除后数据不会丢失。**请勿删除此目录**。

---

## 十一、常见问题

### 问题 1：80 端口无法访问

**症状**：浏览器显示「无法访问此网站」

**排查**：

```bash
# 1. 检查 Nginx 容器是否运行
docker compose ps | grep nginx

# 2. 检查防火墙
sudo ufw status

# 3. 检查云服务器安全组（阿里云/腾讯云控制台）

# 4. 测试本地访问
curl http://localhost
```

### 问题 2：后端启动失败

**症状**：`account-book-backend` 状态为 `Restarting` 或 `Exited`

**排查**：

```bash
# 查看错误日志
docker compose logs backend
```

**常见原因**：
- `.env` 文件中密码/密钥未填写
- 数据库未就绪（等待 30 秒后重试）

### 问题 3：数据库连接失败

**症状**：后端日志显示 `Communications link failure`

**排查**：

```bash
# 1. 检查数据库容器是否运行
docker compose ps | grep db

# 2. 检查数据库健康状态
docker compose logs db | grep "ready for connections"

# 3. 重启数据库
docker compose restart db
```

### 问题 4：内存不足

**症状**：容器频繁重启

**排查**：

```bash
# 查看内存占用
docker stats --no-stream

# 如果总内存超过 1.5GB，考虑升级服务器
```

### 问题 5：前端显示空白

**症状**：浏览器打开后一片空白

**排查**：

```bash
# 检查 Nginx 容器日志
docker compose logs nginx

# 检查前端文件是否存在
docker exec account-book-nginx ls /usr/share/nginx/html
```

---

## 十二、后续升级（CD 部署）

当代码有更新时，可通过 GitHub Actions 自动部署：

1. 打开 GitHub 仓库页面
2. 点击 **Actions** → **CD 部署**
3. 点击 **Run workflow**
4. 输入版本号（如 `v1.1.0`）
5. 点击 **Run workflow** 按钮
6. 等待 5-10 分钟，部署完成

**前提条件**：需要在 GitHub 仓库的 Settings → Secrets and variables → Actions 中配置：

| Secret 名称 | 值 |
|-------------|-----|
| `host_ip` | 服务器 IP 地址 |
| `host_username` | 服务器 SSH 用户名 |
| `my_siyao` | 服务器 SSH 私钥 |

---

## 十三、APK 分发（Android）

林蛮记账 Android App 已构建为 APK 文件，位于：

```
frontend/src-tauri/gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk
```

### 分发方式

| 方式 | 说明 | 适合场景 |
|------|------|---------|
| 直接传 APK | 用微信/QQ/网盘发送 APK，对方下载安装 | 少量内测用户 |
| 放到服务器 | APK 放到服务器上通过 Nginx 提供下载 | 团队内测 |
| CD 部署 | 将 APK 构建加入 GitHub Actions | 正式发布 |

### 手机安装步骤

1. 将 APK 传到手机上（微信文件传输、网盘等）
2. 打开 APK 文件，系统会提示「未知来源」
3. 进入「设置」→「安全」→ 允许「安装未知应用」
4. 返回文件管理器，再次点击 APK 安装
5. 安装完成后打开，手动输入服务器地址即可

> **注意**：当前为 Debug 版本，APK 体积约 490MB（包含调试符号），正式发布时可用 Release 版本缩小体积。

---

## 十四、性能基准

以下数据基于本地开发机的并发测试结果，真实服务器性能可能有差异。

### MockMvc 进程内测试（验证数据正确性）

| 场景 | 请求数 | 成功率 | 吞吐量 | 平均耗时 |
|------|--------|--------|--------|----------|
| 并发注册 | 100 | 100% | 76.7/s | 125ms |
| 并发登录 | 100 | 100% | 106.6/s | 88ms |
| 并发记账 | 200 | 100% | 1282/s | 6ms |
| 并发报表 | 200 | 100% | 1626/s | 4ms |
| 并发站内信 | 100 | 100% | 862/s | 9ms |

### Locust 真实 HTTP 渐进式压测

| 虚拟用户 | 总请求 | 失败率 | 吞吐量 | p99 响应 |
|----------|--------|--------|--------|----------|
| 20 | 449 | 0% | 15.8/s | 200ms |
| 50 | 1,164 | 0% | 39.4/s | 140ms |
| 100 | 2,284 | 0% | 77.0/s | 330ms |
| 200 | 3,049 | 0% | 101.7/s | 2,600ms |
| 500 | 5,046 | 0% | 169.9/s | 3,000ms |

### 结论

- **稳定性**：所有测试 0% 失败率，无数据丢失
- **舒适区**：50~100 人同时操作，响应 < 300ms
- **瓶颈点**：超过 100 并发后，数据库连接池（默认 10）成为瓶颈，p99 延迟明显上升
- **优化建议**：生产环境调大 HikariCP 连接池至 20~30，可大幅提升并发能力

---

## 附录：架构图

```
用户浏览器/手机
        │
        ▼
    Nginx (端口 80)
        │
        ├─→ /api/*  ──→ Spring Boot 后端 (端口 8080)
        │                      │
        │                      ▼
        │              MySQL 数据库 (端口 3307)
        │
        └─→ /*  ──→ 前端静态文件 (React)
```

---

*文档创建日期：2026-07-19*
*最后更新：2026-07-19*
