@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ========================================
echo  记账大王 - APK 构建（连接服务器版）
echo  目标：http://47.104.152.25:8080
echo ========================================
echo.
echo 本窗口需要保持打开，约 5-10 分钟。
echo.

REM 确保 VITE_API_BASE 被注入到前端产物
set VITE_API_BASE=http://47.104.152.25:8080

REM 先构建前端 Web 产物
echo [1/2] 构建前端 Web 产物（约 1 分钟）...
call npm run build:web
if %ERRORLEVEL% neq 0 (
    echo 前端构建失败！请检查上面的错误信息。
    pause
    exit /b 1
)
echo 前端构建完成。

REM 再构建 APK
echo.
echo [2/2] 构建 Android APK（约 5-8 分钟，请耐心等待）...
npx tauri android build --debug --split-per-abi
if %ERRORLEVEL% neq 0 (
    echo APK 构建失败！请检查上面的错误信息。
    pause
    exit /b 1
)

echo.
echo ========================================
echo  构建完成！
echo.
echo  APK 位置：
echo    arm64（多数手机）：src-tauri\gen\android\app\build\outputs\apk\arm64\debug\app-arm64-debug.apk
echo    armeabi（老手机）：src-tauri\gen\android\app\build\outputs\apk\armeabi\debug\app-armeabi-debug.apk
echo.
echo  下一步：把 APK 传到手机并安装
echo    方法1：用 USB 线 + adb install
echo    方法2：用微信/QQ 发送 APK 文件到手机
echo    方法3：用文件管理器 + USB 传文件
echo ========================================
pause
