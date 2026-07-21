# 林蛮记账 · 部署踩坑经验手册

> **用途**：本文件是「经验性记忆」，专门记录这个项目在**构建与 CI/CD 部署**过程中真实踩过的坑和对应的固定解法。
> 目的是让**后续 AI 对话 / 新机器部署 / 改 CI 流程**时少走弯路，直接照坑位排查。
> 与 `DEPLOY_GUIDE.md`（操作手册）、`M6_PLAN.md`（实施计划）是三层分工：**踩坑总结 / 标准步骤 / 设计记录**，互不重复。
>
> 标注说明：`✅已确认` = 来自仓库文件/记忆中明确记录的错误；`⚠️设计性坑` = 来自 docker-compose / 配置注释里写明的"如果不这样就会爆"的陷阱。

---

## 0. 这套部署的架构（先有全局观）

| 形态 | 构建产物 | 部署方式 | 关键文件 |
|------|----------|----------|----------|
| 后端 | Spring Boot 3 jar → Docker 镜像 | `docker compose` 起 `backend` + `db`(MySQL) 容器 | `backend/Dockerfile`、`docker-compose.yml` |
| 前端 | React 静态包 → nginx Docker 镜像 | nginx 反代 `/api` 到后端 | `frontend/Dockerfile`、`frontend/nginx.conf` |
| 安卓 App | Tauri v2 打包 debug/release APK+AAB | 手动拷到手机安装（内测，不上架） | `frontend/src-tauri/` |
| 镜像仓库 | GitHub Container Registry | `ghcr.io/linmanhzy/linman_account/account-book` | `.github/workflows/deploy.yml` |
| CI/CD | GitHub Actions | push 触发 CI 测试；手动 Run workflow 触发 CD 打包推送 | `.github/workflows/ci.yml`、`deploy.yml` |

**关键认知**：前端 Docker 是**纯静态 + nginx 反代**，不是前端直连后端 IP。移动端/桌面端（Tauri WebView、浏览器）才需要 `VITE_API_BASE` 指向服务器且后端开 CORS。

---

## 1. 安卓 App 构建坑（Tauri v2 / Android）✅已确认

只在**本机 Windows** 构建安卓 APK 时遇到，CI 里一般不做安卓构建（量大、慢、需 GUI）。

### 坑 1.1：Gradle 依赖一个都拉不下来
- **现象**：`tauri android build` 卡在下载 Gradle 分发包 / 依赖，全部失败或超时。
- **根因**：国内网络封锁 GitHub、Gradle 官方源、google()/mavenCentral()。
- **固定解法**：在**全局** `C:\Users\<用户>\.gradle\init.gradle` 写华为 maven + 阿里 gradle-plugin 三重镜像（见 4.1）。**必须全局，不能只放项目里**——因为 Tauri 每次 `tauri android init` 会重新生成 `src-tauri/gen/android`，项目内的覆盖会被冲掉。

### 坑 1.2：`:buildSrc` 解析不了 `kotlin-dsl` 插件标记
- **现象**：`Could not resolve plugin ... kotlin-dsl`，或 plugin 标记未知。
- **根因**：`buildSrc` 子项目不走 `pluginManagement`，而是走 `buildscript.repositories` 去解析插件标记。原镜像只注入了 `pluginManagement`。
- **固定解法**：镜像脚本用 `applyMirror` 闭包，把阿里 `gradle-plugin` 镜像**同时注入三条路径**——`pluginManagement`、`allprojects.repositories`、`allprojects.buildscript.repositories`。（详见 4.1）

### 坑 1.3：Kotlin 增量编译跨盘崩溃
- **现象**：`RelocatableFileToPathConverter` 报错，`different roots` 之类，构建直接挂。
- **根因**：Cargo 注册表在 **C 盘**（默认 `~/.cargo`），项目在 **D 盘**，Windows 无法跨盘根算相对路径。
- **固定解法**：全局 `C:\Users\<用户>\.gradle\gradle.properties` 加 `kotlin.incremental=false`。（见 4.2）
- **副作用**：构建变慢，但能过。可接受。

### 坑 1.4（小）：`lib.rs` 两个无害 warning
- 用 `#[cfg(...)]` 守卫 `Manager` 导入 + 闭包参数加 `_app` 前缀，消除编译 warning。不影响产物，但 Android 构建 warning 多会让人误判。

**构建结果**：M6 已成功产出 `app-universal-debug.apk`（≈490MB）与 AAB，`src-tauri/gen/android/app/build/outputs/apk/universal/debug/`。

---

## 2. CI/CD 流水线坑（GitHub Actions + ghcr）✅已确认 / ⚠️设计性

### 坑 2.1：本机 git 推送走 SSH(22) 被封锁
- **现象**：`git push` 用 `git@github.com:...` 一直超时/拒绝。
- **根因**：本机网络 SSH(22 端口) 被封锁。
- **固定解法**：远程统一改用 **HTTPS**。推送时：
  - 用户名：`linmanhzy`
  - 密码：**GitHub Personal Access Token（PAT，需勾 repo + write:packages）**，不是账号密码。
- **AI 提醒**：改了远程 URL 后，CI 用的是 GitHub 自己的 runner，不受影响；但**本机提交/推送**必须走 HTTPS+PAT。配一次 `git remote set-url origin https://github.com/linmanhzy/account_book.git` 即可。

### 坑 2.2：ghcr 推送需要 `GHCR_PAT` 且权限要 `write:packages`
- **现象**：CD 流程 `docker push ghcr.io/...` 报 401/403。
- **根因**：默认 `GITHUB_TOKEN` 对 package 写权限不足，或 secret 名对不上。
- **固定解法**：仓库 Settings → Secrets 配置 `GHCR_PAT`（有 `write:packages` scope）；`deploy.yml` 里 `docker login ghcr.io -u linmanhzy -p ${{ secrets.GHCR_PAT }}`。镜像名必须经 `echo lowercase` 处理（ghcr 全小写）：`ghcr.io/linmanhzy/linman_account/account-book`。
- **AI 提醒**：镜像路径里的 `linman_account` 是固定 org/subpath，**不要**在文档里写成 `你的用户名`——`DEPLOY_GUIDE.md` 那句是给读者占位，真实值是 `linmanhzy/linman_account`。

### 坑 2.3：CI 里 Maven 测试要独立 profile 隔离库
- **现象**：并发测试污染主库或 H2 配置冲突。
- **根因**：并发测试用独立 `concurrency` profile + 库名 `conctestdb` 隔离。
- **固定解法**：复跑 `mvn test -Dtest=Concurrent*Test`（concurrency profile）；常规 `mvn test`。H2 2.x 内存库**默认多线程，不要加 `MULTI_THREADED` 参数**（加上反而报错）。

---

## 3. 服务端部署坑（Docker Compose + 服务器）⚠️设计性（已写进配置注释，偏离就会爆）

### 坑 3.1：后端 JVM Metaspace 不调大就 OOM 启动失败 ✅已确认（写在 docker-compose 注释）
- **现象**：容器 `Restarting`/`Exited`，日志报 `OutOfMemoryError: Metaspace`。
- **根因**：Spring Boot 3 类加载比 2.x 吃 Metaspace，默认 64M 不够。
- **固定解法**：`JAVA_OPTS=-Xmx256m -XX:MaxMetaspaceSize=128m`。内存账：Heap 256M + Metaspace 128M + 线程栈 ≈ 512M，所以容器 `mem_limit: 512m` 是卡着算的，**别再砍内存**，否则必 OOM。

### 坑 3.2：MySQL 必须用 3307 而非 3306
- **现象**：容器起不来或后端连不上库，报端口冲突 / 连到别人库。
- **根因**：服务器上 RAG 项目已占 3306。
- **固定解法**：`docker-compose.yml` 里 MySQL 映射 `3307:3306`，后端 `DB_PORT=3307`。部署前先 `sudo ss -tlnp | grep -E '8080|3306|3307'` 确认冲突。

### 坑 3.3：后端必须等 MySQL 健康再起
- **固定解法**：`depends_on: db: condition: service_healthy` + db 配 `healthcheck`（mysqladmin ping）。否则后端先起连不上库直接挂。`.env` 里 `DB_HOST=db`（compose 内网服务名），不是 `127.0.0.1`。

### 坑 3.4：首次部署必须手动 `scp docker-compose.yml` 到服务器
- **现象**：CD 跑完，服务器 `docker compose ps` 找不到服务 / 用的还是旧配置。
- **根因**：CD 只 `scp` 代码和推送镜像，**不传 `docker-compose.yml`**。
- **固定解法**：首次按 `deploy-server-setup.md` 第七步，本机 `scp docker-compose.yml 用户@IP:/opt/account_book/`。之后 compose 改了也要手动再传一次。

### 坑 3.5：服务器首次 `docker compose up -d` 需先 `docker login ghcr.io`
- **现象**：`pull access denied` / `unauthorized`。
- **固定解法**：服务器上 `docker login ghcr.io`（用有 `read:packages` 的 PAT），否则拉不到私有镜像。
- **AI 提醒**：`mysql_data/` 目录是持久化数据卷，**千万别删**，删了账本全没。

### 坑 3.6：`.env` 没填或 JWT_SECRET 手打会启动失败
- **固定解法**：服务器 `/opt/account_book/.env` 必须填 `DB_PASSWORD`、`JWT_SECRET`(`openssl rand -base64 32` 生成，别手打)、`ADMIN_PASSWORD`、`CORS_ORIGINS`、`APP_VERSION=latest`。JWT_SECRET 太短后端起不来。

### 坑 3.7：CD 在服务器 `docker pull` 阶段 `Run Command Timeout` ✅已确认（首次部署 v1.0.2 实测）
- **现象**：`applescott/ssh-action` 跑到 `docker pull ghcr.io/...` 时卡在某镜像层，最终报 `Run Command Timeout`、exit 1。
- **根因**：(1) ghcr.io 在国内服务器拉取极慢，Spring Boot 后端大层几百 MB 常超默认 10 分钟；(2) 脚本额外 `docker pull ...:latest`（compose 只用 `APP_VERSION`，`:latest` 永不引用），下载量翻倍雪上加霜。
- **固定解法**：`deploy.yml` 的 ssh-action 显式加 `command_timeout: 30m`；把 4 次显式 `docker pull`（含 `:latest`）换成 `docker compose --env-file .env pull`（只拉 compose 实际引用的版本化镜像）。下载量减半 + 超时放宽 3 倍即可过首拉。
- **AI 提醒**：若 30m 仍超时，说明 ghcr 基本被墙，应改用阿里云 ACR 等国内可达仓库（`deploy.yml` 改推 ACR + `docker-compose.yml` 镜像改 ACR 地址 + 服务器 `docker login` ACR）。`mysql:8.0` 由 `docker compose pull` 一并发拉，一般已缓存。

---

## 4. 给新机器 / CI 的"一键避坑"配置（直接抄）

### 4.1 全局 Gradle 镜像 `C:\Users\<用户>\.gradle\init.gradle`
```groovy
def repoConfig = {
    maven { url 'https://mirrors.huaweicloud.com/repository/maven/' }
    maven { url 'https://maven.aliyun.com/repository/public' }
    maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
}
def applyMirror = { repos ->
    repos.clear()
    repoConfig(repos)
}
allprojects {
    buildscript { applyMirror(repositories) }
    applyMirror(repositories)
}
settingsEvaluated { settings ->
    applyMirror(settings.pluginManagement.repositories)
}
```
> 要点：阿里 `gradle-plugin` 必须同时进 `pluginManagement` + `allprojects` + `allprojects.buildscript` 三条路（坑 1.2）。

### 4.2 全局 Gradle 属性 `C:\Users\<用户>\.gradle\gradle.properties`
```properties
kotlin.incremental=false
```
> 解决跨盘增量编译崩溃（坑 1.3）。

### 4.3 后端 JVM（写进 `docker-compose.yml` 的 `backend.environment`）
```yaml
JAVA_OPTS: "-Xmx256m -XX:MaxMetaspaceSize=128m"
```

---

## 5. 经验总结（给后续 AI / 维护者）

1. **Gradle / npm 镜像配置要"全局"不要"项目级"**：Tauri 会重生成 `gen/android`，项目内配置会被冲。安卓构建所有坑的根解都在**用户目录全局文件**。
2. **遇到 `different roots` / `RelocatableFile` 类错误，先想跨盘**：Windows 上 cargo 在 C、项目在 D 是经典雷。
3. **Spring Boot 3 容器化先定内存账**：Heap+Metaspace+线程栈，512M 是这条架构的下限，砍不得。
4. **git 推送先确认走 HTTPS+PAT**：本机 SSH(22) 被封是既定事实，别浪费时间调 SSH key。
5. **ghcr 镜像名全小写 + 固定 subpath**：`linmanhzy/linman_account/account-book`，别照文档占位符写。
6. **CD 不传 compose**：改了 `docker-compose.yml` 记得手动 scp 到服务器。
7. **部署前必查端口冲突**：`ss -tlnp` 看 8080/3306/3307；MySQL 用 3307 避开 RAG。
8. **前端 Docker 是 nginx 反代形态**：`VITE_API_BASE` 只在非 Docker（直连/移动端）时需要；容器内部靠 nginx `/api` 反代，前端无需知道后端 IP。
9. **持久化目录 `mysql_data/` 是命根**：任何 `docker compose down -v` 都别带 `-v`。
10. **验证顺序**：后端先 `docker logs` 看 Metaspace/OOM → `docker compose ps` 看 Up → 浏览器开 `http://IP:8080/swagger-ui.html` 确认存活。

---

## 6. 真机调试坑（Tauri Android App 连本机/局域网后端）✅已确认

只在「手机 App 连电脑本机/局域网后端」调试时遇到；docker 全套部署（前端 nginx 反代）不涉及。

### 坑 6.1：后端 CORS 改了不重启 = 白改
- **现象**：App 登录被 `blocked by CORS policy` 拦，但手机浏览器直开 `/api/health` 却显示 ok。
- **根因**：CORS 是后端**启动时**才读入配置。地址栏直开 health 不算跨域、不验证 CORS，这是"health 能通、登录却不行"的典型表现。改 `application.yml`(本地跑) 或 `CORS_ORIGINS` 环境变量(本地/docker) 后**必须重启后端**才生效。
- **AI 提醒**：docker 跑后端时改 `application.yml` 源文件无效（jar 已固化进镜像），要改 `docker-compose` 用的 `.env` 的 `CORS_ORIGINS` 变量并重启容器；本地 `mvn`/`java` 跑改源文件有效，但要重启进程。

### 坑 6.2：Tauri v2 安卓 WebView 真实 Origin 是 `https://tauri.localhost`
- **现象**：`VITE_API_BASE` 地址对了、后端也起了，App 内登录仍被 CORS 拦。
- **根因**：App 内网页 fetch 带 `Origin: https://tauri.localhost`，不在后端允许源列表里就被拦。后端 `SecurityConfig.corsConfigurationSource()` 用 `setAllowedOriginPatterns`（来源 `myapp.cors.allowed-origins`，可用环境变量 `CORS_ORIGINS` 覆盖）。
- **固定解法**：把 `https://tauri.localhost` 加进允许源；内测图省事直接写 `*` 通配（`allowedOriginPatterns` + `allowCredentials(true)` 下 `*` 合法，Spring 回显实际 origin，不影响凭据）。**生产必须换回具体域名，别留 `*`**。

### 坑 6.3：`VITE_API_BASE` 打包时写死，改地址必重打包 APK
- **现象**：改了 `.env` 的 `VITE_API_BASE` 指向电脑 IP，装到手机还是连不上。
- **根因**：`VITE_API_BASE` 是 Vite **编译期**替换进 App 的固定字符串，运行时改 `.env` 无效。手机上的 `127.0.0.1` 指手机自己，必须用电脑局域网真实 IP。
- **固定解法**：先改 `frontend/.env` 的 `VITE_API_BASE` → 再跑 `build_android.bat` 重打包 → 重装新 APK。**顺序错（先构建后改 .env）则 APK 里还是旧地址**。`android-build.log` 末尾 `BUILD_EXIT_CODE=0` 才算成功。

### 坑 6.4：验证 CORS 最快用 curl，不用 chrome://inspect
- 后端起好后，电脑跑：
  ```
  curl -i -X OPTIONS "http://localhost:8080/api/auth/login" -H "Origin: https://tauri.localhost" -H "Access-Control-Request-Method: POST"
  ```
- 看响应头有无 `Access-Control-Allow-Origin: https://tauri.localhost`（或回显的 origin）→ 有则 CORS 已放行；没有则配置没生效（回头查坑 6.1）。比手机 USB 调试 `chrome://inspect` 看 Console 快得多，先过这关再谈别的。

### 坑 6.5：「能打开 App 前端页面」≠「能联网」
- App 把打包进去的本地网页资源显示出来（登录页能看）是正常的，不代表 `VITE_API_BASE` 地址对。真正联网要看登录请求发出的 URL（chrome://inspect 的 Network）是不是电脑 IP。

---

## 附：本文件覆盖 vs 未覆盖

- ✅ 已覆盖：安卓构建 3 大阻塞（1.1–1.3）、git SSH 封锁、ghcr 权限、JVM Metaspace、MySQL 3307、依赖顺序、首次 scp、登录拉镜像、`.env`、前端反代形态、**真机调试 CORS+IP 直连重打包（见第 6 节）**。
- ⚠️ 未覆盖（文档里没明确记录、若后续遇到请补进本节）：CD 实际跑通过程中的具体报错（如镜像层缓存失败、runner 磁盘满）、release 签名配置（当前只打 debug，上架需 keystore）、HTTPS/域名反代（当前直 IP:8080，未接 Nginx/TLS）。
