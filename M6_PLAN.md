# M6 手机 App 端（仅 Android）· 详细实施计划（Tauri 移动端）

> 基于 **2026-07-19** 当前代码状态更新（M6-1/2/3 已完成，M6-4 进行中）。最初于 2026-07-18 制定，经 7 轮确认。**本计划只做 Android，不做 iOS。**
> 配套总蓝图见 `PROJECT_PLAN.md`（M6 行已更新为 Tauri 移动端 / 仅 Android）。

---

## 0. 已确认的关键决策（边界）

| 项目 | 结论 |
|------|------|
| 技术路线 | **Tauri 移动端**（v2），复用现有 `src/` 网页代码，不重写 React Native |
| 功能范围 | **全功能对齐网页**（记账/分类/概览/报表/导出/反馈/通知/贪吃蛇/管理后台） |
| 平台 | **仅 Android**（用户无 iOS 设备，不面向 iOS 用户开发；所有 iOS 工程/签名/IPA/APNs 内容已删除） |
| 后端地址 | 先用**本机局域网 IP**（已在 `.env` 注入），部署后改 `.env` 即可 |
| 推送 | **已放弃系统推送**（FCM 国内不可用）；仅保留应用内「通知中心」站内信 |
| 分发 | **APK 内测（免费）**：直接安装 apk 文件即可，不上 Google Play，无需付费 |
| 签名 | 本机生成 `dawang.keystore`；**密钥库密码 / 别名密码由用户自记**（绝不由 AI 保管） |
| 名称/包名 | `记账大王` / `com.wangxinchen.dawang` |
| 图标 | 原项目图标全是 1×1 占位图，已用 AI 重生成 1024 源图并经 `tauri icon` 产出全套（含 Android mipmap） |
| 维护与更新 | **纳入本计划**（见 §6，仅 Android 自更新） |

> 与最初 `PROJECT_PLAN.md` 的差异：原方案写「React Native (Expo)」，因代码库已存在 Tauri 桌面壳且 React 网页代码完整，**改为复用同一套 React 代码走 Tauri 移动端**。

---

## 1. 当前代码现状与缺口分析（已探明）

| 文件 / 位置 | 现状 | M6 缺口 / 处理 |
|------|------|---------|
| `src-tauri/tauri.conf.json` | 仅桌面 `app.windows`；`beforeDevCommand:"npm run dev:web"`、`beforeBuildCommand:"npm run build:web"` | **已修正**：`$schema` 改为官方 `config/2`；移除无效的 `app.android` 块（Tauri 2 无此字段，包名由顶层 `identifier` 决定）；`package.json` 已补 `dev:web`/`build:web` 脚本 |
| `package.json` | 脚本只有 `dev`/`build`/`preview` | **已完成**：新增 `dev:web`、`build:web`、`tauri` |
| `src/api/client.ts` | `API_BASE = import.meta.env.VITE_API_BASE \|\| 'http://127.0.0.1:8080'` | **已完成**：`.env` 注入本机局域网 IP；`127.0.0.1` 仅作本地网页回退 |
| `src-tauri/src/lib.rs` | 无条件 `window.open_devtools()`（移动端无此 API，会崩）；注册了未使用的 `tauri-plugin-sql` | **已完成**：`devtools` 用 `#[cfg(desktop)]` 守卫；移除 `tauri-plugin-sql` 插件；移动端构建有 2 个无害 warning（`unused tauri::Manager` / `unused app` 变量），待清理 |
| `src-tauri/Cargo.toml` | 含 `tauri-plugin-sql` 依赖（前端未调用） | **已完成**：移除该依赖 |
| `src-tauri/capabilities/default.json` | 残留 `sql:*` 桌面权限 | **已完成**：清理为仅 `core:default`；因推送方案已放弃，M6-3 未再加 `push:default`（已核实，仅 `core:default`） |
| `index.html` | `viewport` 未做安全区适配 | **已完成**：`viewport-fit=cover` + 禁缩放 |
| `src-tauri/icons/*` | 原图标全是 1×1 占位图（70 字节） | **已完成**：AI 重生成 1024 源图 → `tauri icon` 产出全套（PNG/ICO/ICNS + Android mipmap） |
| `.gitignore` | 未忽略 `.env`/密钥 | **已完成**：追加 `.env`、`*.keystore`（系统推送已放弃，`google-services.json` 等不再需要） |
| `tauri android init` | — | **已完成**：本机环境就绪后已执行，成功生成 `src-tauri/gen/android/`（包名 `com.wangxinchen.dawang`，compileSdk/targetSdk=36、minSdk=24） |
| `src/components/MainLayout.tsx` | 桌面 Sider + 移动 Drawer，亮色主题 | **已完成（M6-1）**：改为底部 Tab Bar（`MobileTabBar`，5 入口），按 `isTauriMobile()`/`isMobileView()` 平台条件渲染；新增 `src/utils/platform.ts` |
| `src/pages/SnakeGame.tsx` | 仅键盘方向键操控 | **已完成（M6-2）**：新增 `src/hooks/useSwipe.ts`，`SnakeCanvas.tsx` 改 `forwardRef`，`SnakeGame.tsx` 接入滑动操控；键盘保留为桌面回退 |
| 后端 `NotificationService` | 仅写站内信 | **已完成（M6-3）**：已**回退** FCM 代码（`DeviceToken`/`PushService`/`FcmPushService` 全部删除），构造函数 `(NotificationRepository, UserRepository)`，仅写站内信（已核实源码） |
| **Android 构建环境** | 本机 **无** Android SDK/NDK/JDK | **环境前置已完成（见 §5.1/§5.3）**：本机已装 SDK/NDK/JDK、配镜像；`tauri android init` 已生成 `src-tauri/gen/android/`；首轮构建见 §5.3 结论 |

---

## 2. 总体架构（仅 Android）

```
┌─────────────────────────────┐
│  记账大王 App (Tauri Android) │
│  ├─ React 网页代码 (复用 src/) │  ← 同一套前端，自动适配窄屏
│  ├─ 底部 Tab Bar (移动导航)   │
│  ├─ 通知中心（站内信）       │  ← 应用内查看通知（无系统推送）
│  └─ @tauri-apps/plugin-updater│ ← App 自更新 (Android)
└──────────────┬──────────────┘
               │ HTTPS (VITE_API_BASE 指向真实服务器)
               ▼
        ┌───────────────────┐
        │  Spring Boot 后端   │  ← 现有接口全部复用（含 /api/notifications 站内信）
        └─────────┬─────────┘
                  ▼
              MySQL 数据库
```

**核心思想**：后端只写一份，网页与 App 共用；移动端只是「换了个外壳 + 加了底部导航 + 接入原生能力（通知中心/自更新）」；系统推送已放弃，仅站内信。

---

## 3. 分阶段任务

### M6-0 · 工程化与可运行基础 ✅（代码/配置已完成，仅 `android init` 待环境）

- [x] **对齐脚本**：`package.json` 新增 `"dev:web": "vite"`、`"build:web": "tsc && vite build"`、`"tauri": "tauri"`
- [x] **配置移动端**：`tauri.conf.json` 修正 `$schema` 为官方 `config/2`；移除无效 `app.android` 块（包名由顶层 `identifier = com.wangxinchen.dawang` 决定，正确）
- [x] **清理权限**：`capabilities/default.json` 移除 `sql:*`，保留 `core:default`
- [x] **lib.rs 守卫**：`open_devtools()` 用 `#[cfg(desktop)]` 包裹，移除未使用的 `tauri-plugin-sql`（否则 Android 编译报错/运行崩溃）
- [x] **API 地址可配置**：`.env` / `.env.example` 注入本机局域网 IP；`client.ts` 已支持 `VITE_API_BASE`
- [x] **`.gitignore`**：忽略 `src-tauri/gen/`、`*.keystore`、`google-services.json`、`*-firebase-adminsdk*.json`、`.env`
- [x] **图标全套**：AI 重生成 1024 源图 → `tauri icon` 产出 PNG/ICO/ICNS + Android 各密度 mipmap
- [x] **验证**：`npm run build:web` 通过（13.99s）；`tauri android init` 配置解析通过，**仅卡在 Android SDK 缺失**（环境前置，见 §5.1）
- [x] **`tauri android init`**：本机已执行，成功生成 `src-tauri/gen/android/`（见 §5.1/§5.3）

### M6-1 · 移动端 UI 适配 ✅（已落地，代码已核实）

- [x] **底部 Tab Bar**：新增 `src/components/MobileTabBar.tsx`（概览 / 记一笔 / 明细 / 报表 / 我的，5 入口）
- [x] **`MainLayout` 平台分流**：桌面保留 Sider/Drawer，移动端渲染 `MobileTabBar` + 内容区（`isTauriMobile()`/`isMobileView()` 判断，不污染 Web）
- [x] **安全区/状态栏适配**：`src/App.css` 禁橡皮筋滚动/长按选中 + `env(safe-area-inset-*)`；`index.html` 已 `viewport-fit=cover`
- [x] **窄屏适配**：各页在 ≤480px 下重排；`Login.tsx` 用 `dvh` 适配；`Dashboard.tsx` 平台化文案；`RecordList.tsx` 移动卡片列表
- [x] **设计语言统一**：沿用 antd 体系与品牌色 `#1677FF`，仅在导航结构与触控交互上移动化
- **验证**：浏览器/桌面模拟移动视口下逐页走查无溢出、无错位（无需 Android SDK 即可开发）✅

### M6-2 · 全功能对齐 + 贪吃蛇触屏 ✅（已落地，代码已核实）

- [x] **功能清单核对**：各页面在移动端逐项验证可用（含管理员后台）
- [x] **贪吃蛇滑动操控**：新增 `src/hooks/useSwipe.ts`，`SnakeCanvas.tsx` 改 `forwardRef`，`SnakeGame.tsx` 接入上下左右滑动转向；保留键盘操控作为桌面回退
- [x] **表单/弹窗移动化**：Ant Design 弹窗在手机满宽、输入友好
- **验证**：模拟移动视口跑通「记账→查看明细→报表→导出→反馈→收通知→玩贪吃蛇」全链路 ✅

### M6-3 · 通知能力（已决策：放弃系统推送）

> 经评估，FCM 在国内基本不可用（依赖 GMS），用户决定**放弃系统推送**，仅保留应用内「通知中心」站内信。

- [x] **站内信接口全可用**：`/api/notifications`（列表/未读/标记已读）、`/api/admin/notifications`（发送）均通过集成测试（见 `backend/TEST_REPORT.md`，54 例全绿）
- [x] **回退 FCM 代码**：`device_token` 表、`/api/device/token`、`PushService`/`FcmPushService`、前端 token 上报等全部删除，`NotificationService` 仅写站内信
- [x] **测试报告**：`backend/TEST_REPORT.md` 已生成并更新结论
- **验证**：通知中心在 Web/桌面/移动端一致可用；`TEST_REPORT.md` 全绿

### M6-4 · Android 打包发布（仅 APK 内测，免费）🔶 进行中

> **2026-07-19 状态**：本机 Android 工具链 + 镜像已就绪（详见 §5.1/§5.3）；`tauri android init` 已生成 `gen/android`。但首轮调试构建（RELAUNCH4，`build_android.bat` → `tauri android build --debug`）**实际失败**：`:buildSrc` 无法解析 `org.gradle.kotlin.kotlin-dsl.gradle.plugin:5.2.0`（走华为 maven 而非阿里 gradle-plugin 镜像），APK 未产出。见 §5.3「阻塞与修复方向」，修后重跑确认 APK。

- [x] **环境前置**：ANDROID_HOME / ANDROID_NDK_HOME、Rust 4 个 Android 目标、`cargo-ndk` v4.1.2、command-line tools、`build_android.bat`、Windows 开发者模式（symlink）、Gradle 镜像（§5.3）均已完成
- [ ] **签名 keystore**：`keytool -genkey -v -keystore dawang.keystore -alias dawang -keyalg RSA -keysize 2048 -validity 10000`（密码用户自记），配置 `tauri.conf.json` 的 `bundle.android` 签名
- [ ] **`tauri android build` 产出 APK**：先修 §5.3 镜像阻塞 → 重跑 `build_android.bat`，确认产出 `src-tauri/gen/android/app/build/outputs/apk/{universal|arm64}/debug/*.apk`（内测直装）+ 可选 AAB
- [ ] **AndroidManifest 权限**：在 `gen/android` 中确认 `INTERNET`（系统推送已放弃，无需 `POST_NOTIFICATIONS`）
- [ ] **自更新**：`plugin-updater` + `TAURI_SIGNING_PRIVATE_KEY`，自托管 `latest.json`
- [ ] **安装/分发文档**：产出步骤说明（免费 APK 内测，无需 Google Play）
- **验证**：APK 安装可运行

---

## 4. 推送方案（已决策：放弃系统推送）

```
管理员发通知
     │
     ▼
NotificationService
     └─ 写 notification 表（站内信）✅ 已实现且在用
        （系统推送已放弃，无 ② 异步下发步骤）
```

- **决策**：FCM 在国内基本不可用（依赖 GMS），经评估**放弃系统推送**；通知仅保留应用内「通知中心」站内信（管理员发送写入 `notification` 表，用户打开 App 查看）。
- **已回退**：`device_token` 表、`/api/device/token` 接口、`PushService`/`FcmPushService`、前端 token 上报等代码全部删除；后端 `NotificationService` 仅写站内信。
- **后续若需真系统推送**：可评估极光 JPush（国内可用，需账号 + 手写 Tauri 原生插件）或各厂商通道，届时再接入。

---

## 5. Android 打包与环境准备

### 5.1 Android 环境前置 ✅（本机已完成，2026-07-19）

`tauri android init` / `tauri android build` 所需工具链**本机已全部就绪**，无需再装：

**本机实际配置（可直接复用）**：
- `ANDROID_HOME` = `D:\software\Android_Stdio\SDK`
- `ANDROID_NDK_HOME` = `D:\software\Android_Stdio\SDK\ndk\30.0.15729638`
- Rust Android 目标（全装）：`aarch64-linux-android`、`armv7-linux-androideabi`、`i686-linux-android`、`x86_64-linux-android`
- `cargo install cargo-ndk` → v4.1.2（`C:\Users\w1590\.cargo\bin`）
- 手动安装 Android command-line tools → `D:\software\Android_Stdio\SDK\cmdline-tools\latest`（原 SDK 缺 `sdkmanager`，Tauri init 需要）
- 开启 **Windows 开发者模式**（`reg add ...\AppModelUnlock /v AllowDevelopmentWithoutDevLicense /t REG_DWORD /d 1`，RunAs 提权）→ 解决 Tauri 构建 symlink 被拒
- `tauri android init` 已成功生成 `src-tauri/gen/android/`（包名 `com.wangxinchen.dawang`，compileSdk/targetSdk=36，minSdk=24）
- Gradle 镜像重定向（华为/阿里）见 **§5.3**，这是本机能拉取依赖的关键

**若换机器重搭，通用步骤**：
1. 安装 **Android Studio**（含 SDK Manager）→ SDK Manager 安装 Android SDK Platform（API 34+）、Build-Tools、NDK（25.x/26.x 或本机 30.x）、Command-line Tools
2. 设 `ANDROID_HOME` 并把 `%ANDROID_HOME%\platform-tools` 与 `%ANDROID_HOME%\cmdline-tools\latest\bin` 加入 `PATH`
3. 装 **JDK 17+**（本机为 JDK 21，可用）
4. 装 Rust Android 目标 + `cargo-ndk`
5. 确认：`sdkmanager --version` / `java -version` / `cargo help` 正常
6. `npm run tauri android init` → `npm run tauri android build`

> 注：原 `tauri.conf.json` 无 `app.android` 块（Tauri 2 由顶层 `identifier` 决定包名，正确）；构建脚本见项目根 `build_android.bat`（注入环境变量后跑 `tauri android build --debug`，日志落 `android-build.log`）。

### 5.2 构建与分发

- 签名：`keytool -genkey -v -keystore dawang.keystore -alias dawang -keyalg RSA -keysize 2048 -validity 10000`
- 构建：`tauri android build` → `src-tauri/gen/android/app/build/outputs/` 下 `app-universal-release.apk`、AAB
- **分发（免费）**：APK 直接发给测试机安装（开启「允许未知来源」）即可，**无需 Google Play、无需付费**
- AAB：仅当上架 Google Play 才需要（需 Play 开发者账号 $25 一次性，本期不做）

### 5.3 镜像与 Gradle 配置（本机关键，2026-07-19）

> 本机网络环境：**github.com:443 不可达、plugins.gradle.org TLS 握手被终止、services.gradle.org 307 跳转到 github**。Gradle 分发与依赖默认源全部拉不到，必须用国内镜像，否则 `tauri android build` 卡死/失败。

**镜像分工（通过全局 `C:\Users\w1590.gradle\init.gradle` 重定向）**：

| 用途 | 镜像地址 |
|------|---------|
| Gradle 分发包（gradle-8.14.3-bin.zip） | 华为 `https://mirrors.huaweicloud.com/gradle/` |
| mavenCentral + google 依赖仓库 | 华为 `https://mirrors.huaweicloud.com/repository/maven/` |
| Gradle Plugin Portal（`.gradle.plugin` 标记构件，如 `kotlin-dsl`） | 阿里 `https://maven.aliyun.com/repository/gradle-plugin/` |

- Gradle 分发已手动下载 `gradle-8.14.3-bin.zip`（华为镜像，约 131MB）放入 `~/.gradle/wrapper/dists/gradle-8.14.3-bin/<hash>/`。
- `init.gradle` 用 `pluginManagement { repositories { ... } }` 把插件门户重定向到阿里 gradle-plugin；用 `allprojects/repositories` 把 mavenCentral+google 重定向到华为。
- 构建入口：`build_android.bat`（项目根）注入 `ANDROID_HOME`/`ANDROID_NDK_HOME`/`PATH` 后跑 `npx tauri android build --debug`，日志输出到 `android-build.log`（分离启动以绕过长任务超时）。

**⚠️ 首轮构建实测失败的两处阻塞与最终修复（2026-07-19 已全部解决，APK 已产出）**：

**阻塞 1：`:buildSrc` 无法解析 `kotlin-dsl` 插件标记 `org.gradle.kotlin.kotlin-dsl.gradle.plugin:5.2.0`**
- 根因：本环境下 `buildSrc` 并不走 `pluginManagement` 解析该标记，而是从 **buildscript `repositories`** 解析；原 `init.gradle` 只把阿里 gradle-plugin 加进了 `pluginManagement`，没加进 `buildscript.repositories`，于是标记只去华为 maven 找（找不到——标记仅存在于 Gradle 插件门户，不在 Maven Central）。
- 修复（全局 `C:\Users\w1590.gradle\init.gradle`）：用 `applyMirror` 闭包把阿里 gradle-plugin 镜像**同时注入** `allprojects { repositories }` 与 `allprojects { buildscript { repositories } }`；`pluginManagement` 收敛为 `[阿里 gradle-plugin, gradlePluginPortal()]`。该配置是**全局 init 脚本**，不受 `tauri android build` 重新生成 `gen/android` 影响。

**阻塞 2：Kotlin 增量编译跨盘根崩溃 `IllegalArgumentException: this and base files have different roots`**
- 根因：Tauri 的 Android Kotlin 源码位于 `C:\Users\w1590\.cargo\registry\...`（cargo 注册表，C 盘），项目在 `D:` 盘；Kotlin 增量编译的 `RelocatableFileToPathConverter` 无法跨盘根计算相对路径，直接抛异常。
- 修复（全局 `C:\Users\w1590.gradle\gradle.properties`）：`kotlin.incremental=false`。关闭增量编译后编译器走非可重定位（non-relocatable）路径，跨盘错误消失。（备选方案：把 cargo 注册表移到 D: 或项目移到 C:，但改 Gradle 属性最省事、无需挪文件。）
- 实测：关闭增量后 Kotlin/Java 编译仅有 `lib.rs` 的 2 个无害 warning，APK 正常产出。

**构建结果（2026-07-19 验证通过）**：
- `BUILD SUCCESSFUL`，产出 debug 通用 APK 与 AAB：
  - APK：`src-tauri/gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk`（约 490MB，含 4 个 ABI + 调试符号）
  - AAB：`src-tauri/gen/android/app/build/outputs/bundle/universalDebug/app-universal-debug.aab`
- 构建入口：`build_android.bat`（项目根）注入 `ANDROID_HOME`/`ANDROID_NDK_HOME`/`PATH` 后跑 `npx tauri android build --debug`，日志落 `android-build.log`（分离启动绕过长任务超时）。
- `lib.rs` 移动端构建仍有 2 个无害 warning（`unused tauri::Manager` / `unused app` 变量），可清理（加 `#[allow(unused_imports, unused_variables)]` 或 `_app` 前缀），不影响构建。

---

## 6. 后期维护与 App 更新（Android）

### 6.1 版本管理
- App 端 `tauri.conf.json` 的 `version` 单一来源，每次发版 +1
- 后端新增 `GET /api/system/version`，返回 `{ latestVersion, minSupportedVersion, forceUpdate, downloadUrl }`
- App 启动请求该接口：`version < minSupportedVersion` → 强制跳转下载；否则正常进入

### 6.2 更新机制（仅 Android）
- 用 `@tauri-apps/plugin-updater`（Tauri 官方）+ **代码签名私钥**（`TAURI_SIGNING_PRIVATE_KEY`）
- 自托管更新服务器：放置 `latest.json` + 签名后的更新包（APK 全量）
- App 内检测 → 下载 → 安装，无需上架商店即可更新

### 6.3 后端向后兼容
- 新增/修改接口保持旧客户端可用：不删除字段、不改语义；新增字段向后兼容
- 破坏性变更必须升 `minSupportedVersion` 并提前在站内信/公告告知用户

### 6.4 密钥与签名材料管理（重要）
| 材料 | 用途 | 存放 |
|------|------|------|
| `dawang.keystore` + 密码 | Android 签名 | **密码管理器 / CI Secrets**，绝不进 Git |
| `TAURI_SIGNING_PRIVATE_KEY` | 更新包签名 | CI Secrets |
| （系统推送已放弃，无 FCM 相关密钥） | — | — |

- `.gitignore` 已追加：`*.keystore`、`google-services.json`、`*-firebase-adminsdk*.json`、`.env`

### 6.5 CI/CD 建议（可选）
- **GitHub Actions** `build-android`：ubuntu runner，`tauri android build` 产出 APK，上传 Artifacts；`git tag v*` 触发发版并生成 `latest.json`

### 6.6 崩溃与遥测（可选）
- 接入轻量错误上报（如 Sentry `tauri` SDK），仅收集必要崩溃栈，不涉及账本明文

---

## 7. 执行状态（§7 原「待确认清单」已全部确认）

| # | 原问题 | 结论 |
|---|--------|------|
| 1 | 后端地址 | 本机局域网 IP（`.env` 已注入），部署后改 |
| 2 | Firebase / 系统推送 | **已放弃**：FCM 国内不可用（依赖 GMS），仅保留应用内「通知中心」站内信（M6-3 已回退全部推送代码） |
| 3 | iOS | **不做**（仅 Android） |
| 4 | Android 签名 | 本机生成 keystore，密码用户自记 |
| 5 | 分发渠道 | **APK 内测（免费）**，不上 Google Play |
| 6 | 图标/名称 | `记账大王`/`com.wangxinchen.dawang`；图标 AI 重生成全套 |

---

## 8. 验证与交付物

| 阶段 | 验证方式 | 交付物 | 状态 |
|------|---------|--------|------|
| M6-0 | `build:web` 通过；`tauri android init` 已生成 `gen/android` | 工程化基础就绪 | ✅ |
| M6-1 | 移动视口逐页走查（代码已核实） | 移动端 UI 适配完成 | ✅ |
| M6-2 | 全链路跑通 + 贪吃蛇滑动（代码已核实） | 全功能对齐 | ✅ |
| M6-3 | 站内信可用 + `backend/TEST_REPORT.md` 54 例全绿；FCM 已回退 | 通知可用 + 测试报告 | ✅ |
| M6-4 | `BUILD SUCCESSFUL`，已产出 debug 通用 APK + AAB（见 §5.3 结果）；安装文档待补 | 可安装 APK（免费内测） | ✅ APK 已产出（2026-07-19） |
| 维护 | §6 各机制落地 | 可持续维护的 App | ⏳ |

> 执行顺序严格遵循：M6-0 → M6-1 → M6-2 → M6-3 → M6-4。每阶段完成并验证后再进入下一阶段。
> **当前阻塞**：M6-4 首轮 `tauri android build` 因 Gradle 镜像未命中 kotlin-dsl 插件而失败（§5.3），修镜像后重跑即可；不影响 M6-1/M6-2/M6-3 已完成的功能。
