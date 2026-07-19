@echo off
set ANDROID_HOME=D:\software\Android_Stdio\SDK
set ANDROID_NDK_HOME=D:\software\Android_Stdio\SDK\ndk\30.0.15729638
set PATH=D:\software\Android_Stdio\SDK\cmdline-tools\latest\bin;D:\software\Android_Stdio\SDK\platform-tools;C:\Users\w1590\.cargo\bin;%PATH%
cd /d d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\frontend
call npx tauri android build --debug > d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\frontend\android-build.log 2>&1
echo BUILD_EXIT_CODE=%ERRORLEVEL% >> d:\itcast\vibeCoding\vibeCoding_HeiMa\account_book\frontend\android-build.log
