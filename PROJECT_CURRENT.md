# 林蛮记账 · 项目当前状况（PROJECT_CURRENT）

> 最后更新：2026-07-22。本文件记录项目**当前进展与动态**，每次操作都会更新；经验与全局记忆见 `CODEBUDDY.md`。

## 项目概况（速览）
- 名称：林蛮记账（多用户联网记账系统）
- 技术栈：Spring Boot（`backend/`）+ React + Tauri v2（`frontend/`，同一套代码产出 Web 与 Android APK）
- 当前形态：Web 端 + 安卓 App（内测免费，不上 Google Play，不做 iOS）
- 包名/标识：记忆包名 `com.linman.accountbook`；当前 `tauri.conf.json` 标识为 `com.wangxinchen.dawang`，产品名「记账大王」

## 近期进展

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
