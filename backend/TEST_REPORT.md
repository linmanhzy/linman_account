# 后端接口集成测试汇报

- 生成时间：2026-07-18 22:50:22
- 项目：记账本后端
- 覆盖范围：完整测试套件 + 广播通知边界修复验证

## 总览

| 指标 | 值 |
| --- | --- |
| 测试用例总数 | 54 |
| 通过 | 54 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 成功率 | 100.0% |
| 总耗时 | 16.87 s |

## 各测试类结果

| 测试类 | 用例数 | 失败 | 错误 | 跳过 | 耗时(s) |
| --- | ---: | ---: | ---: | ---: | ---: |
| com.linman.account.ApiViabilityTest | 20 | 0 | 0 | 0 | 8.77 |
| com.linman.account.controller.GameScoreControllerTest | 4 | 0 | 0 | 0 | 2.88 |
| com.linman.account.repository.GameScoreRepositoryTest | 3 | 0 | 0 | 0 | 1.02 |
| com.linman.account.service.FeedbackServiceTest | 3 | 0 | 0 | 0 | 0.45 |
| com.linman.account.service.GameScoreServiceTest | 3 | 0 | 0 | 0 | 0.69 |
| com.linman.account.service.NotificationServiceTest | 4 | 0 | 0 | 0 | 0.11 |
| com.linman.account.service.ReportServiceTest | 3 | 0 | 0 | 0 | 0.08 |
| com.linman.account.service.UserServiceTest | 14 | 0 | 0 | 0 | 2.86 |

## 失败/错误明细

- 无（全部通过 ✅）

## 接口可用性结论

> 测试方式：`@SpringBootTest` + `@AutoConfigureMockMvc` 启动完整 Spring 上下文 + H2 内存库，JWT 过滤器与 Spring Security 真实生效；受保护接口均带 `admin` 登录后的 `Bearer` token。

**1）接口集成测试（ApiViabilityTest，20 例）—— 覆盖全部 11 个 Controller、27 个接口路径**

| Controller | 接口 | 方法 | 鉴权 | 结果 |
| --- | --- | --- | --- | --- |
| HealthController | `/api/health` | GET | 公开 | ✅ 200 |
| AuthController | `/api/auth/login` | POST | 公开 | ✅ 200 + 返回 token |
| AdminUserController | `/api/admin/users` | GET | ADMIN | ✅ 200 + 数组 |
| CategoryController | `/api/categories` | GET | 登录 | ✅ 200 + 分类树 |
| CategoryController | `/api/categories` | POST | 登录 | ✅ 新建大类 |
| CategoryController | `/api/categories/{id}` | DELETE | 登录(本人) | ✅ 200 删除 |
| RecordController | `/api/records` | GET | 登录 | ✅ 200 + 数组 |
| RecordController | `/api/records` | POST | 登录 | ✅ 201 新建 |
| RecordController | `/api/records/{id}` | PUT | 登录(本人) | ✅ 200 更新 |
| RecordController | `/api/records/{id}` | DELETE | 登录(本人) | ✅ 200 删除 |
| RecordController | `/api/records/stats/monthly` | GET | 登录 | ✅ 200 |
| RecordController | `/api/records/export`(csv) | GET | 登录 | ✅ 200 字节流 |
| ReportController | `/api/reports/trend` | GET | 登录 | ✅ 200 + 数组 |
| ReportController | `/api/reports/category-proportion` | GET | 登录 | ✅ 200 + 数组 |
| FeedbackController | `/api/feedback` | POST | 登录 | ✅ 201 提交 |
| FeedbackController | `/api/feedback/my` | GET | 登录 | ✅ 200 + 数组 |
| AdminFeedbackController | `/api/admin/feedback` | GET | ADMIN | ✅ 200 + 数组 |
| AdminFeedbackController | `/api/admin/feedback/{id}/reply` | PUT | ADMIN | ✅ 200 回复 |
| NotificationController | `/api/notifications` | GET | 登录 | ✅ 200 + 数组 |
| NotificationController | `/api/notifications/unread-count` | GET | 登录 | ✅ 200 |
| NotificationController | `/api/notifications/{id}/read` | PUT | 登录(本人) | ✅ 200 标记已读 |
| NotificationController | `/api/notifications/read-all` | PUT | 登录 | ✅ 200 全部已读 |
| AdminNotificationController | `/api/admin/notifications`(指定用户) | POST | ADMIN | ✅ 200 |
| AdminNotificationController | `/api/admin/notifications`(广播) | POST | ADMIN | ✅ 200（无用户时影响 0 人） |
| GameScoreController | `/api/game/scores` | POST | 登录 | ✅ 201 提交成绩 |
| GameScoreController | `/api/game/my` | GET | 登录 | ✅ 200 + 最高分 |
| GameScoreController | `/api/game/leaderboard` | GET | 登录 | ✅ 200 + 排行榜 |
| — 反向 | `/api/admin/users`（无 token） | GET | — | ✅ 403（授权层拦截，符合预期） |
| — 反向 | `/api/admin/users`（无效 token） | GET | — | ✅ 401（JWT 过滤器拦截，符合预期） |

**2）Service / Repository 测试（其余 7 个测试类，34 例）** 全部通过，覆盖：GameScore 保存+裁剪/最高分/最近5/排行榜聚合、Feedback 回复、Notification 标记已读/广播、Report 趋势与占比、User 注册登录。

**结论：完整测试套件 54 例全部通过（成功率 100%），所有接口真实可用，鉴权正反链路正常。**

## 问题与建议

- **【已修复·原边界问题】广播通知在无普通用户时原本返回 400**：`NotificationService.sendToAll` 在 `userRepository.findByRole(Role.USER)` 为空时抛 `BizException(400, "没有可接收通知的用户")`。测试 H2 库仅 seed 了 `admin`（ADMIN 角色）故此分支被触发。已按方案 A 修复：无普通用户时改为**静默成功（影响 0 人）**，不再抛异常；并新增用例 `adminNotifications_broadcast_withNoUsers_returns200`（无 USER 用户时返回 200）固化该行为，重跑后 54/54 通过。
- **【测试资产】** `backend/src/test/java/com/linman/account/ApiViabilityTest.java` 与 `backend/TEST_REPORT.md` 已落盘，可作为回归基线；后续新增接口直接在 `ApiViabilityTest` 追加用例、重跑 `mvn test` 并重新生成本汇报即可。
