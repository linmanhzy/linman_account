# 安卓 App 分发与公网部署计划（A→B 阶段）

> **状态**：A 阶段已完成并验证；B 阶段待做（等系统其它要求完善后再着手）。
> **目标**：让「林蛮记账」安卓 App 既能装得上（签名 release 包），又能发给任意朋友公网可用（不局限于同一 WiFi）。
> **最近更新**：2026-07-21

---

## 〇、阶段划分总览

| 阶段 | 内容 | 状态 | 产物 |
|------|------|------|------|
| **A 阶段** | 能安装的 release 包（签名 + 体积优化 + 内测放行 http） | ✅ 已完成 | `app-arm64-release.apk`（12 MB，已签名） |
| **B 阶段** | 公网可用（后端上服务器 + dynv6 域名 + CORS + App 改域名重打包） | ⬜ 待做 | 公网域名 + 重打的 APK |

> **当前能力边界**：A 阶段产出的包连的仍是电脑局域网后端（`http://10.70.14.127:8080`），只能「同一 WiFi 下自测」。要「发给所有朋友公网可用」，必须做 B 阶段。

---

## A 阶段：可安装的 release 包（已完成 ✅）

### A-1 打包命令改为拆包 release【build_android.bat】
- 改动：`frontend/build_android.bat` 第 6 行
  - `--debug` → `--split-per-abi`（release 是 Tauri 默认，无需写 `--release`）
- 效果：按 CPU 架构拆成独立 APK，体积从 debug 万能包 ~467 MB 降到单包 ~12 MB。

### A-2 release 签名配置【build.gradle.kts + keystore】
- 密钥（一次性生成，已就位，位于 `frontend/src-tauri/gen/android/app/` 下，被 `.gitignore` 忽略，不泄密）：
  - `release-key.jks`：密钥库
  - `keystore.properties`：别名 `linman`、密码 `LinmanKey2026!`、storeFile `release-key.jks`
- `build.gradle.kts`（gen/android/app/）改动：
  - 新增 `signingConfigs{ create("release"){ … } }`，**必须放在 `buildTypes` 之前**（否则 `getByName("release")` 报 `SigningConfig with name 'release' not found`）
  - release 块挂 `signingConfig = signingConfigs.getByName("release")`

### A-3 内测放行 http 明文【build.gradle.kts】
- release 块加 `manifestPlaceholders["usesCleartextTraffic"] = "true"`
- ⚠️ 上 https 后改回 `"false"`。

### A-4 构建与验证结果
- `BUILD_EXIT_CODE=0` ✅
- 产物目录：`frontend/src-tauri/gen/android/app/build/outputs/apk/`
  - `arm64/release/app-arm64-release.apk` → **12.0 MB**（手机用这个）
  - `arm/release/app-arm-release.apk` → 9.3 MB（32 位老设备）
  - `x86_64/release/app-x86_64-release.apk` → 11.6 MB（模拟器）
  - `x86/release/app-x86-release.apk` → 11.8 MB（模拟器）
- 签名验证：`apksigner verify` 通过，证书 DN=`CN=Linman,OU=AccountBook,O=Linman` ✅

### A 阶段踩坑记录（已解决）
1. **签名配置位置**：`signingConfigs` 必须在 `buildTypes` 之前。
2. **全局代理挡路**：`C:\Users\w1590\.gradle\gradle.properties` 强制走 Clash 代理 `127.0.0.1:7897`，Clash 没开时 release 下载 `lint-gradle:31.13.0` 失败。当时**临时关闭该代理完成构建并事后恢复**，未动用户其它环境。
   > 项目级 `gradle.properties` 写空 `systemProp.https.proxyHost=` 无效（Gradle 忽略空值，全局值仍生效）；真正要做时是改全局文件或开着 Clash。

### A 阶段重要提醒（重打包前必读）
- ⚠️ **`gen/android` 会被 `tauri android init` 重新生成**：`build.gradle.kts` 改动、`keystore.properties`、`release-key.jks` 全会丢失。若需重初始化，先把 `release-key.jks` 备份到 `gen/` 外（否则无法用同一密钥更新已发布 App）。
- 当前包仍连局域网后端，属于「同 WiFi 自测」形态。

### A-5 分发方式（已落地）：GitHub Releases 自动发布
- CD 流水线（`.github/workflows/deploy.yml` 的 `build-apk` 任务）在每次部署后，会把 4 个架构的 release APK 发布到 GitHub Release（标签 `v<版本号>`）。
- 给别人下载用**固定「最新版」链接**（始终指向最新 Release 的对应架构包）：
  - `https://github.com/linmanhzy/linman_account/releases/latest/download/app-arm64-release.apk`（绝大多数 2015 年后手机）
  - `https://github.com/linmanhzy/linman_account/releases/latest/download/app-arm-release.apk`（老 32 位机）
- 安装需手机允许「未知来源」；因每次 CD 都出新 Release，上述 `latest/download/` 链接自动指向最新包。

---

## B 阶段：公网可用（待做 ⬜）

> 详细零基础操作见 `docs/dynv6-guide.md`；服务器 docker 部署见 `DEPLOY_GUIDE.md` 与 `docs/deploy-experience.md`。下面只列落地清单。

### B-1 确定部署场景（先决策）
- **场景 A**：有云服务器 / 固定公网 IP → 域名静态填一次即可，无需 DDNS 客户端。最省事。
- **场景 B**：家里电脑/宽带当服务器 → 需路由器端口映射 + DDNS 客户端自动更新。
- 决策点：公网 IP 是否固定、是否有真公网 IP（路由器 WAN 口 IP 是否等于步骤1查到的公网 IP）。

### B-2 申请免费域名（dynv6）
- 注册 `https://dynv6.com`，创建 `linman.dynv6.net`（Type=IPv4）。
- 记下 **Token**（DDNS 更新钥匙）。
- 参考 `docs/dynv6-guide.md` 步骤 3。

### B-3 绑定 IP / 配 DDNS
- 固定 IP：网页静态填一次（步骤 4.1）。
- 动态 IP：放更新脚本（Linux `update_dynv6.sh` + cron，或 Windows 任务计划 `update_dynv6.bat` 每 5 分钟），见 `docs/dynv6-guide.md` 步骤 4.2。

### B-4 服务器部署后端 + 前端（docker compose）
- 首次手动 `scp docker-compose.yml` 到服务器（CD 不传 compose）。
- 服务器 `.env` 关键行：
  ```env
  CORS_ORIGINS=http://localhost:5173,https://tauri.localhost,http://linman.dynv6.net:8080
  DB_HOST=db
  ```
  > 生产不要写 `*`；把 App 源 `https://tauri.localhost` 和 Web 域名都列上。CORS 改完**必须重启后端容器**才生效。
- `docker compose --env-file .env up -d`，确认 `curl http://localhost:8080/api/health` 返回 ok。
- 家庭宽带场景需路由器端口映射 8080→内网机器 8080（步骤 5.1）。

### B-5 App 改用域名并重打包（关键）
- 改 `frontend/.env`：
  ```env
  VITE_API_BASE=http://linman.dynv6.net:8080
  ```
  > `VITE_API_BASE` 是编译期写死的，改了**必须重跑 `build_android.bat` 并重装 APK**，手机上 127.0.0.1 指手机自己、必须用域名。
- 重跑 `build_android.bat`，等 `android-build.log` 末尾 `BUILD_EXIT_CODE=0`。
- 装新 APK，登录请求即发往域名。

### B-6 验证
- Web：浏览器开 `http://linman.dynv6.net:8080` 看到前端页。
- App：打开 App 登录，数据走域名到服务器。
- CORS 校验（可选）：
  ```
  curl -i -X OPTIONS "http://linman.dynv6.net:8080/api/auth/login" -H "Origin: https://tauri.localhost"
  ```
  看响应头有无 `Access-Control-Allow-Origin: https://tauri.localhost`。

### B 阶段待确认/待补项（系统其它要求完善后再定）
- 是否上 https（小绿锁 / 无端口）：需自备域名 + 国内备案 + TLS，属付费升级，本文先不展开。
- 备案与端口：当前用 8080 高位端口 + 免费子域名，个人自用一般不强制备案。
- 服务器选型（云服务器 or 家里机器）需在开始 B 前与用户确认。

---

## 一页纸流程（先 A 后 B）

```
A 阶段（已完成）                              B 阶段（待做）
打包→拆包release ─┐                          dynv6 申请域名 + Token
签名配置 + 放行http ─┤→ app-arm64-release.apk   IP 绑定 / DDNS 脚本
apksigner 验证通过 ─┘   (12MB, 同WiFi自测)  →  docker 部署 + CORS_ORIGINS
                                            →  App 改 VITE_API_BASE 重打包
                                            →  公网可用（任意朋友）
```

---

## 关联文档
- `docs/dynv6-guide.md`：免费域名 + DDNS 零基础完整教程（B 阶段主参考）
- `DEPLOY_GUIDE.md`：服务器部署操作手册
- `docs/deploy-experience.md`：部署踩坑经验手册
- 记忆条目「Tauri 安卓 release 构建要点（签名/代理/体积）」：A 阶段坑位速查
