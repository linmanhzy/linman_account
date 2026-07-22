@echo off
setlocal EnableDelayedExpansion
REM ============================================================
REM 本地调试构建：产出指向服务器的 Debug APK（含详细报错，适合排查）
REM
REM 与 CD 构建（build_android.sh）的对齐改进：
REM   - 使用 set VITE_API_BASE 进程环境变量注入（Vite 最高优先级），
REM     不再仅依赖 .env 文件写入（解决了 .env 未被正确读取时
REM     API_BASE 回落空字符串→登录报 "Failed to construct 'URL'" 的 bug）
REM   - 增加 URL 格式校验，空值或非法 URL 直接退出
REM   - 构建前打印 Vite 能看到的 VITE_API_BASE 值，方便人工确认
REM
REM 用法：双击运行，或在 frontend/ 目录执行 build_debug_server.bat
REM 默认目标：服务器 http://47.104.152.25:8080
REM 自定义目标：set DEBUG_SERVER_URL=http://你的IP:8080 && build_debug_server.bat
REM ============================================================

REM ---- 目标服务器地址（可被环境变量 DEBUG_SERVER_URL 覆盖）----
if not defined DEBUG_SERVER_URL set DEBUG_SERVER_URL=http://47.104.152.25:8080

REM ---- 切到脚本所在目录（即 frontend/）----
cd /d "%~dp0"

echo ============================================================
echo [build_debug_server] 目标服务器：!DEBUG_SERVER_URL!
echo ============================================================

REM ---- 0) 校验 URL 格式 ----
REM 简单校验：必须以 http:// 或 https:// 开头
set TEST_URL=!DEBUG_SERVER_URL!
if "!TEST_URL:~0,7!" neq "http://" (
    if "!TEST_URL:~0,8!" neq "https://" (
        echo [错误] DEBUG_SERVER_URL 格式不正确：!DEBUG_SERVER_URL!
        echo        必须以 http:// 或 https:// 开头
        exit /b 1
    )
)
echo [0/5] URL 格式校验通过：!DEBUG_SERVER_URL!

REM ---- 1) 注入 VITE_API_BASE 为进程环境变量（Vite 最高优先级，对标 CD 的 export）----
REM 关键：这是本次修复的核心 —— 之前只写 .env 文件，Vite 可能读不到或被覆盖。
REM 进程环境变量是 Vite 最高优先级，确保 VITE_API_BASE 一定被注入进 APK。
set VITE_API_BASE=!DEBUG_SERVER_URL!
echo [1/5] 已设置 VITE_API_BASE=!VITE_API_BASE! ^(进程环境变量，Vite 最高优先级^)

REM ---- 1.5) 同步写入 .env 文件（双保险，npm run dev 时也能用）----
if exist .env copy /Y .env .env.debug_backup >nul
(echo VITE_API_BASE=!DEBUG_SERVER_URL!)> .env
echo        .env 文件也已同步写入 ^(npm run dev 可用^)

REM ---- 2) 清理 Vite 构建缓存（防止旧值残留）----
if exist dist rmdir /s /q dist
if exist node_modules\.vite rmdir /s /q node_modules\.vite
echo [2/5] 已清理 dist/ 和 Vite 缓存

REM ---- 3) 初始化安卓工程（若 gen/android 尚未生成）----
npx tauri android init
echo [3/5] tauri android init 完成

REM ---- 4) 注入明文 HTTP 放行 + 签名配置 ----
if exist "src-tauri\gen\android\app\build.gradle.kts" (
    python3 scripts\inject_android_release_config.py src-tauri\gen\android\app\build.gradle.kts
) else (
    echo [警告] 未找到 build.gradle.kts，注入跳过
)
echo [4/5] 注入配置完成

REM ---- 5) 构建 Debug APK ----
REM tauri android build --debug：
REM   - Vite 仍以 production 模式构建前端资源（PROD=true, DEV=false）
REM   - 但 Android Gradle 以 debug 构建类型出包（免签名，直接安装）
REM   - 拆分 ABI（--split-per-abi），只产各架构独立 APK，大幅缩小体积
echo [5/5] 开始构建 Debug APK ...
echo         Vite 编译时的 VITE_API_BASE = !VITE_API_BASE!
npx tauri android build --debug --split-per-abi

REM ---- 还原原始 .env ----
if exist .env.debug_backup (
    move /Y .env.debug_backup .env >nul
    echo [还原] .env 已恢复
)

echo ============================================================
echo BUILD COMPLETE
echo APK 位置：
echo   arm64-v8a（多数手机）：src-tauri\gen\android\app\build\outputs\apk\arm64\debug\app-arm64-debug.apk
echo   armeabi-v7a（老手机）：src-tauri\gen\android\app\build\outputs\apk\armeabi\debug\app-armeabi-debug.apk
echo ============================================================
echo 验证方法：
echo   1. 安装 APK 到手机
echo   2. 打开 App → 应能看到登录页
echo   3. 如果 API_BASE 解析失败，页面顶部会显示红色错误覆盖层
echo   4. 打开 chrome://inspect 查看 WebView console 获取详细信息
echo ============================================================
