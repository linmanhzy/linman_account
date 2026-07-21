@echo off
setlocal EnableDelayedExpansion
REM ============================================================
REM 安卓打包：按运行环境自动注入 VITE_API_BASE（编译期写死进 APK）
REM
REM 环境自动识别（无需手动改任何文件）：
REM   - 本地开发/内测：  直接运行本脚本 → LOCAL 模式 → 用 .env 里的局域网 IP
REM   - 本地出发布版：   先 set LM_ENV=server 再运行 → SERVER 模式 → 用 .env.server 公网域名
REM   - GitHub Actions： 自动识别 GITHUB_ACTIONS 变量 → SERVER 模式
REM
REM SERVER 模式优先级（从高到低）：
REM   1) 已存在的 VITE_API_BASE 进程环境变量（如 CD secret 注入，最高，可覆盖一切）
REM   2) 仓库内 .env.server 文件里的 VITE_API_BASE
REM   都找不到则报错退出。
REM ============================================================

if not defined ANDROID_HOME set ANDROID_HOME=D:\software\Android_Stdio\SDK
if not defined ANDROID_NDK_HOME set ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\30.0.15729638
set PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;C:\Users\w1590\.cargo\bin;%PATH%
cd /d d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\frontend

REM ---- 1) 选环境 ----
set CFG=local
if defined GITHUB_ACTIONS set CFG=server
if /I "%LM_ENV%"=="server" set CFG=server
if /I "%LM_ENV%"=="local"  set CFG=local

REM ---- 2) 注入 VITE_API_BASE（process.env 优先级最高，会盖过 .env）----
if "%CFG%"=="server" (
  if not defined VITE_API_BASE (
    if exist ".env.server" (
      for /f "usebackq tokens=1* delims==" %%A in (`findstr /B "VITE_API_BASE=" ".env.server"`) do set "VITE_API_BASE=%%B"
    )
  )
  if not defined VITE_API_BASE (
    echo [build_android] 错误：SERVER 模式但找不到 VITE_API_BASE（请通过 secret 注入，或在 .env.server 中配置）
    exit /b 1
  )
  echo [build_android] SERVER 模式，后端地址 = !VITE_API_BASE!
) else (
  echo [build_android] LOCAL 模式，使用 .env 中的本地地址
)

REM ---- 2.5) 确保 release 包放行明文 HTTP 且已签名（写回 gen/android/build.gradle.kts）----
REM 关键：若曾跑过 `tauri android init` 重生成 gen/android，会抹掉手工改动；
REM 这里统一用可单测的脚本补回（与 build_android.sh/CI 同一份逻辑），避免 release 包静默禁 http。
if exist "src-tauri\gen\android\app\build.gradle.kts" (
  python3 scripts\inject_android_release_config.py src-tauri\gen\android\app\build.gradle.kts
) else (
  echo [build_android] 警告：未找到 gen/android/build.gradle.kts，签名/明文放行跳过（首次需先 tauri android init）
)

REM ---- 3) 构建（VITE_API_BASE 作为进程环境变量被子进程继承，写死进 APK）----
call npx tauri android build --split-per-abi > d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\frontend\android-build.log 2>&1
echo BUILD_EXIT_CODE=%ERRORLEVEL% >> d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\frontend\android-build.log
