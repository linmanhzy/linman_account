@echo off
chcp 65001 >nul
set HOST=http://localhost:8080

:menu
cls
echo ============ 林蛮记账 并发压测 (Locust 虚拟用户) ============
echo 后端需先在 %HOST% 运行（mvn spring-boot:run 或已部署服务）
echo.
echo 1. 安装依赖   (pip install -r requirements.txt)
echo 2. 预注册用户 (generate_users.py --count 50)
echo 3. Web UI 模式 (浏览器打开 http://localhost:8089 可视化配置)
echo 4. 快速压测   (50 并发 / 1 分钟 -> report_quick.html)
echo 5. 极限压测   (100 并发 / 3 分钟 -> report_extreme.html)
echo 6. 分场景逐个跑 (Auth/Record/Report/Notification/Mixed 各 1 分钟)
echo 0. 退出
echo.
set /p choice=请选择 [0-6]: 

if "%choice%"=="1" goto install
if "%choice%"=="2" goto genusers
if "%choice%"=="3" goto webui
if "%choice%"=="4" goto quick
if "%choice%"=="5" goto extreme
if "%choice%"=="6" goto scenarios
if "%choice%"=="0" exit
goto menu

:install
python -m pip install -r requirements.txt
pause
goto menu

:genusers
python generate_users.py --count 50 --base-url %HOST%
pause
goto menu

:webui
echo 正在启动 Web UI，请在浏览器打开 http://localhost:8089 设置并发数后点击 Start
python -m locust -f locustfile.py --host %HOST%
pause
goto menu

:quick
python -m locust -f locustfile.py --host %HOST% --users 50 --spawn-rate 5 --run-time 1m --headless --html=report_quick.html
pause
goto menu

:extreme
python -m locust -f locustfile.py --host %HOST% --users 100 --spawn-rate 10 --run-time 3m --headless --html=report_extreme.html
pause
goto menu

:scenarios
for %%S in (AuthUser RecordUser ReportUser NotificationUser MixedUser) do (
  echo.
  echo ===== 分场景压测: %%S =====
  python -m locust -f locustfile.py %%S --host %HOST% --users 30 --spawn-rate 5 --run-time 1m --headless --html=report_%%S.html
)
echo.
echo 所有分场景报告已生成：report_AuthUser.html 等
pause
goto menu
