# 服务器部署操作手册

> ⚠️ 本手册面向非技术用户。每一步都附上解释和命令，直接复制粘贴到终端执行即可。

---

## 第一步：登录你的服务器

打开你电脑上的终端（或者 SSH 工具），输入：

```bash
ssh 你的用户名@你的服务器IP
```

比如 `ssh root@123.456.78.90`，输密码后进入服务器。

---

## 第二步：检查现有项目占用的端口

你的 RAG 项目可能已经在用某些端口。先看看有没有冲突：

```bash
# 查看所有正在监听的端口
sudo ss -tlnp | grep -E '8080|3306|3307'
```

- 如果 **8080** 被占用：后续可以改 docker-compose.yml 里的端口映射
- 如果 **3306** 被占用：正常，我们用的是 **3307**，不冲突
- 如果 **3307** 被占用：告诉我，我帮你改端口
- 如果上面命令什么都没输出：说明没有冲突，直接下一步

---

## 第三步：安装 Docker

Ubuntu 一键安装命令（逐条执行）：

```bash
# 1. 安装 Docker
curl -fsSL https://get.docker.com | sudo sh

# 2. 让你不用每次都加 sudo
sudo usermod -aG docker $USER

# 3. 重新加载用户组（生效上一步配置）
newgrp docker

# 4. 验证安装成功
docker --version
```

如果最后一行输出了版本号（如 `Docker version 27.x.x`），就装好了。

---

## 第四步：开放防火墙端口

你的后端要对外开放 **8080** 端口，否则外面访问不了：

```bash
# Ubuntu 默认防火墙
sudo ufw allow 8080/tcp

# 如果用的是云服务器（阿里云/腾讯云等），还要去网页控制台的「安全组」里添加 8080 入站规则
```

---

## 第五步：创建工作目录

```bash
# 创建项目目录
mkdir -p /opt/account_book

# 进入目录
cd /opt/account_book
```

---

## 第六步：创建 .env 环境变量文件

在 `/opt/account_book/` 下创建 `.env` 文件：

```bash
nano /opt/account_book/.env
```

然后把以下内容粘贴进去（**把「请替换」开头的值改掉**）：

```env
# 数据库 root 密码（自己设一个复杂点的）
DB_PASSWORD=你设的数据库密码

# JWT 密钥（用下面命令生成，不要手打）
JWT_SECRET=用openssl rand -base64 32生成的结果

# 管理员初始密码
ADMIN_PASSWORD=你设的管理员密码

# 前端允许访问的地址（把 IP 换成你的服务器 IP）
CORS_ORIGINS=http://你的服务器IP:5173,http://你的服务器IP:8080

# 镜像版本（不要改这行）
APP_VERSION=latest
```

**生成 JWT 密钥的方法**：在服务器终端执行 `openssl rand -base64 32`，把输出结果填到 `JWT_SECRET=` 后面。

按 `Ctrl+X` → `Y` → `回车` 保存。

---

## 第七步：创建 compose 文件（首次手动）

在 `.github/workflows/deploy.yml` 旁边，项目里已经有个 `docker-compose.yml`。首次你需要手动把它传到服务器：

**在你自己的电脑上（不是在服务器上）**，执行：

```bash
# 替换 YOUR_SERVER_IP 和 YOUR_USERNAME 为实际值
scp docker-compose.yml YOUR_USERNAME@YOUR_SERVER_IP:/opt/account_book/
```

> 以后每次 GitHub CD 触发时会自动同步这个文件，这一步只在第一次需要。

---

## 第八步：首次启动

回到**服务器终端**，执行：

```bash
cd /opt/account_book

# 拉取镜像并启动
docker compose --env-file .env up -d
```

第一次启动会自动从 GitHub 下载镜像（约 300MB），可能需要几分钟。

等它完成后，确认两个容器都在运行：

```bash
docker compose ps
```

如果 `STATUS` 列都显示 `Up`（而不是 `Restarting` 或 `Exited`），就成功了。

---

## 第九步：验证

在你**自己电脑的浏览器**里访问：

```
http://你的服务器IP:8080/swagger-ui.html
```

应该能看到一个 API 文档页面（Swagger UI），说明后端正常运行。

---

## 日常使用

### 发布新版本

1. 在 GitHub 仓库页面点 **Actions** → **CD 部署** → **Run workflow**
2. 输入版本号（如 `v1.1.0`）
3. 点绿色的 **Run workflow** 按钮
4. 等 5-10 分钟，它会自动打包、推送镜像、重启服务器容器

### 查看日志

```bash
# 后端日志
docker logs account-book-backend

# 数据库日志
docker logs account-book-db

# 实时跟踪（Ctrl+C 退出）
docker logs -f account-book-backend
```

### 停止 / 启动

```bash
cd /opt/account_book
docker compose --env-file .env down    # 停止
docker compose --env-file .env up -d   # 启动
```

### 数据库数据在哪

MySQL 的数据文件在 `/opt/account_book/mysql_data/` 目录。这个目录是独立的，即使 Docker 容器被删除，数据也不会丢。**千万别删这个目录**。

---

## 常见问题

### 8080 端口无法访问

1. 检查防火墙：`sudo ufw status`
2. 如果用的是云服务器，去网页控制台检查「安全组」有没有放行 8080
3. 检查容器是否在跑：`docker compose ps`

### 后端启动后马上退出

```bash
# 查看日志找原因
docker logs account-book-backend
```

常见原因：
- `.env` 没配好（密码、密钥没填）
- 数据库还没就绪（等 30 秒重试）

### 内存不够

2C2G 跑 MySQL + 后端没问题。如果加了 RAG 项目后内存紧张：

```bash
# 查看各容器内存占用
docker stats --no-stream
```
