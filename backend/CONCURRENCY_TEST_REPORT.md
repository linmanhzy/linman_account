# 林蛮记账 · 并发测试报告

> 生成时间：2026-07-19
> 测试对象：Spring Boot 后端（`account_book/backend`）
> 测试方式：**混合方案** —— JUnit 5 验「数据正确性」 + Locust 虚拟用户验「真实 HTTP 并发性能」

---

## 一、结论速览

| 维度 | 结果 |
| --- | --- |
| 数据正确性（并发下） | ✅ 全部通过，0 数据丢失、余额恒等、错误率 0% |
| 性能压测（真实并发） | ✅ 449 请求 / 0 失败，峰值吞吐 ≈ 15.8 req/s，p99 ≈ 200ms |
| 整体结论 | ✅ 后端在高并发下数据一致、服务稳定 |

---

## 二、数据正确性测试（JUnit，进程内 MockMvc）

运行：`mvn test -Dtest=Concurrent*Test`（独立 H2 库 `conctestdb`，不污染现有 54 个测试）

| 测试类 | 场景 | 并发规模 | 总请求 | 错误率 | 正确性断言 |
| --- | --- | --- | --- | --- | --- |
| ConcurrentAuthTest | 并发登录 | 10 线程 × 10 = 100 | 100 | 0% | 全部拿到 token |
| ConcurrentAuthTest | 并发注册 | 10 × 10 = 100 | 100 | 0% | 注册用户均可登录（无串号/丢失） |
| ConcurrentRecordTest | 并发记账 | 10 × 20 = 200 | 200 | 0% | 记录不丢（数=200）+ 余额=收入−支出（接口值=DB 重算值） |
| ConcurrentReportTest | 并发报表（边写边读） | 10 × 20 = 200 + 1 写线程 | 200 | 0% | 读请求全成功 + 月报始终满足 余额==收入−支出（无脏读/错乱） |
| ConcurrentNotificationTest | 并发站内信 | 10 × 10 = 100 | 100 | 0% | 通知不丢（总数恒=50）+ 未读+已读==总数 |

> 关键不变量验证：由于余额是**现算**（收入−支出）而非存储字段，并发记账后
> 「接口返回的 balance == 数据库重算 sum(income)−sum(expense)」成立，证明无丢失更新、无算术错乱。

---

## 三、性能压测（Locust 虚拟用户，真实 HTTP）

运行：后端在 `http://localhost:8080`（MySQL）运行 → `python -m locust -f locustfile.py --host http://localhost:8080 --users 20 --spawn-rate 5 --run-time 30s --headless --html=report_quick.html`

本轮基线（20 虚拟用户 / 30 秒）：

| 接口 | # 请求 | 平均(ms) | p95(ms) | p99(ms) | 最大(ms) | 失败 |
| --- | --- | --- | --- | --- | --- | --- |
| POST /api/auth/login | 98 | 96 | 100 | 220 | 217 | 0 |
| POST /api/auth/register | 25 | ~110 | 120 | 200 | 196 | 0 |
| POST /api/records | 98 | 13 | 20 | 38 | 37 | 0 |
| GET /api/reports/trend | 73 | 7 | 11 | 20 | 14 | 0 |
| GET /api/reports/category-proportion | 36 | 6 | 10 | 12 | 11 | 0 |
| GET /api/records/stats/monthly | 16 | 6 | 15 | 15 | 15 | 0 |
| GET /api/notifications | 63 | 6 | 10 | 18 | 18 | 0 |
| GET /api/notifications/unread-count | 34 | 5 | 9 | 11 | 11 | 0 |
| **聚合** | **449** | **35** | **110** | **200** | **217** | **0** |

> 可见：读写类接口（记账/报表/站内信）延迟极低（p99 < 40ms）；登录因走 Security 鉴权重算
> JWT 略慢（p99 ≈ 220ms），属正常。整体错误率 0%，服务在 20 并发下稳定。

---

## 四、如何复跑

### 1）数据正确性（JUnit，零安装）
```bash
cd account_book/backend
mvn test -Dtest=Concurrent*Test
```
- 使用独立 profile `concurrency`（库名 `conctestdb`），与默认测试互不干扰。
- 新增文件：`src/test/java/com/wangxinchen/dawang/concurrency/*.java`、`src/test/resources/application-concurrency.yml`。

### 2）性能压测（Locust，需 Python）
```bash
# ① 确保后端已在 http://localhost:8080 运行
cd account_book/backend/tests/loadtest
python -m pip install -r requirements.txt        # 首次安装
python generate_users.py --count 50              # 预注册测试用户（可选）
python -m locust -f locustfile.py --host http://localhost:8080   # Web UI 模式（localhost:8089）
# 或一键菜单：
run.bat
```
- `run.bat` 提供：安装依赖 / 预注册用户 / Web UI / 快速 50 并发 / 极限 100 并发 / 分场景逐个跑。
- 报告：`--html=report_quick.html` 等会落在 `tests/loadtest/`。

---

## 五、注意事项

1. **H2 vs MySQL**：JUnit 正确性跑在 H2（快、确定性高，纯验逻辑）；Locust 压的是真实运行的后端（本次为 MySQL）。**绝对 QPS 以 MySQL 为准**，H2 数字不可直接对比生产容量。
2. **测试数据污染**：Locust 会向运行中的后端写入 `loadtest_` 前缀的用户与记账记录；压测后可在数据库清理 `WHERE username LIKE 'loadtest_%'`。
3. **Locust 字段适配**：本项目登录响应 token 在 `data.token`（不同于 RAG 项目的 `access_token`），`locustfile.py` 已相应处理；Bearer 头格式一致。
4. **扩展**：`locustfile.py` 内含 `AuthUser / RecordUser / ReportUser / NotificationUser / MixedUser` 五个场景类，可用 `locust -f locustfile.py <类名>` 单独压某一接口。
