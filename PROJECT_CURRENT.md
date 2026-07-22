# 林蛮记账 · 项目当前状况（PROJECT_CURRENT）

> 最后更新：2026-07-22。本文件记录项目**当前进展与动态**，每次操作都会更新；经验与全局记忆见 `CODEBUDDY.md`。

## 项目概况（速览）
- 名称：林蛮记账（多用户联网记账系统）
- 技术栈：Spring Boot（`backend/`）+ React + Tauri v2（`frontend/`，同一套代码产出 Web 与 Android APK）
- 当前形态：Web 端 + 安卓 App（内测免费，不上 Google Play，不做 iOS）
- 包名/标识：记忆包名 `com.linman.accountbook`；当前 `tauri.conf.json` 标识为 `com.wangxinchen.dawang`，产品名「记账大王」

## 近期进展

### 2026-07-22 修复 CD Release APK 登录报 `Network Error`（WebView 混合内容拦截）
- 现象：手机浏览器能访问 `http://47.104.152.25:8080/api/health`（网络通），但 Release APK 内登录报 `Network Error`，后端收到 "Error parsing HTTP request header"（疑似 WebView HTTP→HTTPS 升级后 TLS 握手打到了 Tomcat HTTP 端口）。
- 根因：Tauri v2 WebView 源是 `https://tauri.localhost`（HTTPS），fetch `http://47.104.152.25:8080` 被 WebView 判定为混合内容（Mixed Content）→ 在 WebView 层拦截，请求未离开手机。`android:usesCleartextTraffic="true"` 只控制 App 层，管不到 WebView 内部的混合内容策略。
- 修复：**`inject_android_release_config.py`** 新增三个函数：(1) `_write_network_security_config()` 生成 `res/xml/network_security_config.xml`（`cleartextTrafficPermitted="true"`）；(2) `_inject_nsc_into_manifest()` 向 AndroidManifest.xml 注入 `android:networkSecurityConfig` 属性；(3) `inject_all()` 统一入口（向后兼容 `inject()` 旧 API）。`build_android.sh` 和 `build_debug_server.bat` 自动获得 NSC 注入（无需改动，`__main__` 改为调 `inject_all`）。
- 测试：`tests/test_inject_android_release_config.py` 新增 4 个 NSC 用例（生成/注入/幂等/完整性），共 **10 tests passed, 0 failed**。
- 排查文档更新：`docs/debug-build-troubleshooting.md` 新增第八章「WebView 混合内容拦截」。

### 2026-07-22 修复 build_debug_server.bat 产出的 Debug APK 登录报 "Failed to construct 'URL': invalid URL"（TDD 完成）
- 现象：`build_debug_server.bat` 打完的 Debug APK 在手机登录时抛 "Failed to construct 'URL': invalid URL"，旧版仅写 `.env` 文件注入 VITE_API_BASE，Vite 构建时可能未正确读取导致 API_BASE 回落到空字符串。
- 根因：仅 `.env` 文件注入（Vite 中等优先级）不可靠；CD 的 `build_android.sh` 用 `export VITE_API_BASE`（进程环境变量，最高优先级）则没问题。
- 修复（两处）：
  1. **`build_debug_server.bat`**：改为 `set VITE_API_BASE` 进程环境变量注入（对标 CD），增加 URL 格式校验和构建前人工确认日志。
  2. **`client.ts` + 新建 `apiBase.ts`**：将 API_BASE 解析逻辑提取为可测试纯函数 `resolveApiBase()`，PROD 模式下 VITE_API_BASE 为空时抛出详细中文错误（而非静默回落空字符串），并增加 `showFatalErrorOverlay()` 将错误注入 DOM 红色覆盖层（手机用户也能看到）。
- CD Release 版本检查：**无此问题**——`deploy.yml` 通过 env 注入 + `build_android.sh` 有空值 exit 1 防护。
- 测试：新建 `src/api/apiBase.test.ts`，vitest 2.1.0 + jsdom 环境，**14 tests passed, 0 failed**；TypeScript 编译通过（exit 0）。
- 文件清单：新建 `apiBase.ts`、`apiBase.test.ts`、`vitest.config.ts`、`docs/debug-build-troubleshooting.md`；修改 `client.ts`、`build_debug_server.bat`、`tsconfig.json`、`package.json`。
- 排查文档：`docs/debug-build-troubleshooting.md` 含根因分析、修复方案、验证步骤、检查清单。

### 2026-07-22 代码审查：CD 产出的 APK 能否连后端（已修复 2 坑）
- 任务：用 TDD + 代码审查核验「GitHub CD 打出的 APK 能否访问服务器后端」。
- 链路核验（正确，无需改）：`deploy.yml` 把 `VITE_API_BASE=server_api_base`（默认 `http://47.104.152.25:8080`）注入 `build_android.sh`→Vite 编译进 APK→`client.ts` 使用；release 包已注入 `usesCleartextTraffic=true` 放行 http。
- 发现并修复 2 个会让「新部署直接连不上」的坑：
  1. `.env.production` 模板的 `CORS_ORIGINS` 漏了 `https://tauri.localhost`（而 `deploy.yml` 从不写该项，全靠服务器 `.env`）；→ 已补上。
  2. `docker-compose.yml:54` 把 `CORS_ORIGINS` 透传空串时，`application.yml` 的 `${CORS_ORIGINS:*}` 兜底 `*` **不生效**（空值算"已设置"），`SecurityConfig` 解析出空允许列表→所有源被拒，APK 静默登录失败。→ 已在 `SecurityConfig.corsConfigurationSource()` 加兜底：空列表时回落为 `["*"]`。
- 测试：新增 `backend/src/test/java/com/wangxinchen/dawang/config/SecurityConfigCorsTest.java`（纯单元，不启 Spring/DB），先红（空值用例失败）后绿，`mvn test -Dtest=SecurityConfigCorsTest` → Tests run: 3, Failures: 0。
- 结论：当前线上那台服务器能连（用户已手动加 Tauri 源+放行 8080）；但仓库模板此前不自洽，现已修。外部依赖仍需人工确认：① 阿里云安全组入站放行 8080；② `47.104.152.25:8080` 为有效后端地址。

### 2026-07-22 服务器版 App 打包前置验证全部通过（已完成）
- 目标：打一个连「服务器后端 http://47.104.152.25:8080」的 release App，并验证装上后能正常登录。
- 服务器侧前置项（用户已确认）：① `/opt/account_book/.env` 的 `CORS_ORIGINS` 已加 `https://tauri.localhost`；② 阿里云安全组已放行 8080；③ `47.104.152.25:8080` 为有效地址。
- 验证（从本机 curl 实测，全绿）：
  - 测试1 公网可达 `GET /api/health` → `200 {"status":"ok"}`。
  - 测试2 CORS 预检 `OPTIONS /api/auth/login` + `Origin: https://tauri.localhost` → 返回 `Access-Control-Allow-Origin: https://tauri.localhost` + `Allow-Credentials: true`（证明 .env 改后后端已重启生效）。
  - 测试3 登录 `POST /api/auth/login` admin / `WXChen5437@` → `code:0` 返回 token（**admin 登录密码是 `WXChen5437@`，带 @；之前少打 @ 才 401**；该密码与 MySQL 库密码撞值但属两套配置：`ADMIN_PASSWORD` vs `DB_PASSWORD`）。
- 结论：网络 + 8080 + CORS + 登录链路已完整验证可用，打包出的 App 连该地址必能登录。
- 待执行：实际出包（路线 A：GitHub Actions 手动 Run workflow `CD 部署`，填 version + 默认 `server_api_base=http://47.104.152.25:8080`，自动签名出 release APK 到 Releases；或路线 B：本地 `frontend/` 下 `set LM_ENV=server && build_android.bat`）。

### 2026-07-22 排查并修复 App 登录 15s 超时（已完成）
- 现象：手机 App 前端页能开，点登录卡很久后超时；电脑端后端与 DB 正常、APK 已用最新 VITE_API_BASE 重打包、CORS 默认 `*`。
- 根因：Windows 防火墙拦截 8080 入站。手机（10.70.7.108）→ 电脑（10.70.14.127:8080）的 TCP 连接被 DROP，请求干等至 axios 15s 超时；与 IP/CORS/APK 配置无关。
- 修复：未改业务代码；加 8080 入站规则 `netsh advfirewall firewall add rule name=AccountBook_8080 dir=in action=allow protocol=TCP localport=8080` 后重开防火墙，手机 App 正常访问。
- 验证链路：关防火墙→手机浏览器能开 swagger（确认网络层问题）→ 加规则重开防火墙→手机 App 能访问。
- 经验：本机测 App 连本机后端，防火墙必须放行 8080；用“先关防火墙二分”定位网络层，再补精确入站规则，勿长期裸奔；电脑 IP 为 DHCP，变了要改 `frontend/.env` 重打包。

### 2026-07-22 修复 tauri.conf.json 的 bundle.targets 无效配置（已完成）
- 现象：`build_android.bat`（`frontend/` 下，非 `android-build.bat`）跑 `npx tauri android build --split-per-abi` 报 schema 错误，`bundle > targets` 下的 `"apk"` 不符合 `anyOf` 模式。
- 根因：Tauri v2 的 `bundle.targets` 只接受桌面包类型 `["deb","rpm","appimage","nsis","msi","app","dmg"]` 或 `"all"`，`apk`/`aab` 不在枚举内；Android 的 APK/AAB 由 `tauri android build` 命令本身自动产出，不由 `bundle.targets` 控制。属配置语法错误，非依赖缺失或 CLI 版本问题。
- 改动：`frontend/src-tauri/tauri.conf.json` 删除 `"targets": "apk",` 一行（`bundle.android` 节点本身合法，保留）。
- 验证：`python` 校验 JSON 合法（`JSON OK`）；重跑 `build_android.bat` 即可越过该 schema 错误进入真正构建。

### 2026-07-21 后端本地启动数据库连接修复（已完成）
- 现象：`mvn spring-boot:run` 报 `com.mysql.cj.jdbc.ConnectionImpl.createNewIO` 失败，根因 `Access denied for user 'root'@'localhost'`。
- 根因：`backend/src/main/resources/application.yml` 数据库密码为 `password: ${DB_PASSWORD:change-me-db-password}`；未设置环境变量时回退到无效假密码。
- 本机环境：MySQL root 密码 `WXChen5437@`，库 `linman_account`，地址 `127.0.0.1:3306`。
- 已落地方案：新建 `backend/run-dev.bat`（已 gitignore），用命令行参数直传密码 `mvn spring-boot:run -Dspring-boot.run.arguments=--spring.datasource.password=WXChen5437@`，100% 可靠。
- 验证：后端已完整启动（Tomcat started on 8080），`curl http://localhost:8080/` 返回 403（服务正常、未登录）。

### 手机 App（M6）进展
- M6-1/M6-2/M6-3 已完成：移动端底部 Tab Bar + 滑动操控 + 站内信（已放弃 FCM 系统推送，仅应用内通知中心）。
- M6-4 已产出 debug 通用 APK + AAB（约 490MB）。
- release 构建与签名、明文 HTTP 放行等见 `M6_PLAN.md`、`docs/deploy-experience.md`。

## 当前状态与下一步
- 当前可用：Web 前后端正常运行；安卓 `tauri.conf.json` 配置错误已修复，待重跑 `build_android.bat` 验证完整构建。
- 待办 / 关注：
  - 重跑 `build_android.bat` 验证 Android 构建越过 schema 错误后能否成功产出 APK（注意全局 gradle 代理/镜像与 `kotlin.incremental=false` 等已知坑，见 `docs/deploy-experience.md`）。
  - release 签名与明文 HTTP 放行已通过 `scripts/inject_android_release_config.py` 脚本处理，不受 `tauri android init` 重生成 gen/android 影响。
