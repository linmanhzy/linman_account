# 后端接口集成测试汇报

- 生成时间：2026-07-19 09:31:29
- 项目：记账本后端
- 覆盖范围：全部 Controller 接口（已放弃系统推送，仅站内信通知中心）

## 总览

| 指标 | 值 |
| --- | --- |
| 测试用例总数 | 54 |
| 通过 | 54 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 成功率 | 100.0% |
| 总耗时 | 27.53 s |

## 各测试类结果

| 测试类 | 用例数 | 失败 | 错误 | 跳过 | 耗时(s) |
| --- | ---: | ---: | ---: | ---: | ---: |
| com.wangxinchen.dawang.ApiViabilityTest | 20 | 0 | 0 | 0 | 16.64 |
| com.wangxinchen.dawang.controller.GameScoreControllerTest | 4 | 0 | 0 | 0 | 3.90 |
| com.wangxinchen.dawang.repository.GameScoreRepositoryTest | 3 | 0 | 0 | 0 | 1.73 |
| com.wangxinchen.dawang.service.FeedbackServiceTest | 3 | 0 | 0 | 0 | 0.56 |
| com.wangxinchen.dawang.service.GameScoreServiceTest | 3 | 0 | 0 | 0 | 1.03 |
| com.wangxinchen.dawang.service.NotificationServiceTest | 4 | 0 | 0 | 0 | 0.18 |
| com.wangxinchen.dawang.service.ReportServiceTest | 3 | 0 | 0 | 0 | 0.12 |
| com.wangxinchen.dawang.service.UserServiceTest | 14 | 0 | 0 | 0 | 3.38 |

## 失败/错误明细

- 无（全部通过 ✅）

## 接口可用性结论（由 AI 填写）

> 测试方式：`@SpringBootTest` + `@AutoConfigureMockMvc` 启动完整 Spring 上下文 + H2 内存库，JWT 过滤器与 Spring Security 真实生效；受保护接口均带 `admin` 登录后的 `Bearer` token。

**1）接口集成测试（ApiViabilityTest，20 例）—— 覆盖全部 11 个 Controller、27 个接口路径**

- /api/health：✅ 可用（公开）
- /api/auth/login：✅ 可用，返回 token（公开）
- /api/admin/users：✅ 鉴权后可用；无 token → 403、伪造 token → 401（鉴权链路正常）
- /api/categories：✅ 树查询与「创建→删除」链路可用
- /api/records：✅ 完整 CRUD 链路（创建/查询/更新/删除）+ 月度统计 + CSV 导出可用
- /api/reports/trend、/api/reports/category-proportion：✅ 可用
- /api/feedback：✅ 提交与「我的反馈」可用
- /api/admin/feedback：✅ 列表与回复链路可用
- /api/notifications：✅ 列表、未读计数、标记已读/全部已读可用
- /api/admin/notifications：✅ 指定用户发送 + 全体广播（无 USER 时静默成功）可用
- /api/game/scores、/api/game/my、/api/game/leaderboard：✅ 可用

**2）关于系统推送（FCM / Firebase）**

- 经评估，FCM 在国内基本不可用（依赖 GMS），且需额外 Firebase 项目与外部前置，**已决定放弃系统推送**。
- 通知能力仅保留**应用内「通知中心」站内信**：管理员发送通知写入 `notification` 表，用户在 App 内查看（已通过全部接口测试验证）。
- 历史方案中新增的 `device_token` 表、设备 token 注册接口、`PushService`/`FcmPushService`、前端推送上报等代码已**全部回退删除**，保持代码库干净、无死代码。

## 问题与建议（由 AI 填写）

- 当前通知为「拉取式」（用户打开 App 后从通知中心查看），App 关闭/后台时不会主动弹窗。若后续确需「后台/关闭也能收到」的真系统推送，可考虑：极光 JPush（国内可用，但需账号 + 手写 Tauri Android 原生插件）或各厂商推送通道；届时再评估接入成本。
- 其余既有接口未发现可用性问题，54 个用例全部通过。
