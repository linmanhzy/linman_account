# 设计计划：通知体系增强 + 排行榜用户名保护

> 日期：2026-07-20
> 来源需求：用户提出 4 项改动（排行榜隐私、定时通知 bug、定点事件通知、欢迎语改名+第 N 位用户）
> 流程：brainstorming 设计 → 本文档 → test-driven-development 编码
> 关联经验文档：`docs/deploy-experience.md`（部署坑位，本次涉及 JVM 时区与 docker-compose）

---

## 1. 目标与已确认决策

| 需求 | 决策（来自澄清问答） |
|------|----------------------|
| 贪吃蛇排行榜用户名保护 | **后端遮蔽**：保留首字、遮掉后几个字（如 `小王`→`小**`）；本人仍显示全名+（我）。名字不出服务器。 |
| 定时通知 bug | 修两处：①触发逻辑改为"仅在 sendTime 起 2 分钟窗口内触发"，杜绝晚设即发；②调度强制 `Asia/Shanghai` 时区。 |
| 定点（事件）通知 | 本次实现：**欢迎(注册后首次登录)+第 N 位用户** 与 **第一笔账单** 两类事件通知。 |
| 欢迎语时机 | **注册后第一次登录时合并发送**（欢迎语改名"记账大王" + 告知"你是第 N 位用户"）。 |
| 第 N 位用户 | 用 `created_at` 计算：N = `count(users where created_at <= 本人 created_at)`。 |

**范围外（本次不做）**：WEEKLY 频率（当前 `Frequency` 仅 DAILY/SPECIFIC_DATE）、release 签名、HTTPS/TLS。

---

## 2. 功能设计

### 2.1 排行榜用户名遮蔽（后端为主）

- `GameScoreService.leaderboard(int limit, Long currentUserId)`：
  - 对每条记录：若 `userId == currentUserId` → 用真实用户名，`me=true`；否则 `username = mask(name)`，`me=false`。
  - `mask(name)`：`name.length()<=1` 原样返回；否则 `name.charAt(0) + "*".repeat(max(name.length()-1, 2))`（保留首字，至少 2 个星）。
- `LeaderboardEntry` DTO 增加 `Boolean me`。
- `GameScoreController.leaderboard` 传入 `SecurityHelper.getCurrentUserId()`。
- 前端 `SnakeGame.tsx`：self 判定由 `item.username === username` 改为 `item.me`；用户名直接用后端返回的（已遮蔽）。`types.ts` 的 `LeaderboardEntry` 加 `me?`。

### 2.2 定时通知 bug 修复

`ScheduledNotificationDispatcher`：
- 新增常量 `SEND_WINDOW_MINUTES = 2`。
- 时间判定改为窗口：`now.toLocalTime()` 满足 `!now.isBefore(st) && now.isBefore(st.plusMinutes(WINDOW))`。
  - 效果：设置 7:30、当前 20:00 → 不在窗口 → **不立即发**；次日 7:30~7:32 内扫描 → **触发**；服务在 7:31 重启也能补发。
- 保留 `lastFireDate` 同日去重。
- 日期判定不变（DAILY 每天 / SPECIFIC_DATE 指定日）。
- 时区：`SchedulerConfig` 加 `@PostConstruct` 设 `TimeZone.setDefault(Asia/Shanghai)`，使 `LocalDateTime.now()` 用上海时间。`application.yml` 加 `spring.jackson.time-zone: Asia/Shanghai`。`docker-compose.yml` 的 `JAVA_OPTS` 加 `-Duser.timezone=Asia/Shanghai`（部署生效）。

### 2.3 欢迎语 + 第 N 位用户（首次登录触发）

- `User` 增加字段 `firstLoginGreetingSent`（Boolean，默认 false），`schema.sql` 幂等 `ALTER` 加列 `first_login_greeting_sent`。
- `AuthService.register()`：删除 `createWelcomeNotification(saved)` 调用（欢迎不再在注册时发）。
- `AuthService.login()`：校验通过后，若 `!user.isFirstLoginGreetingSent()`：
  - `int nth = userRepository.countByCreatedAtLessThanEqual(user.getCreatedAt())`；
  - 调用 `notificationService.sendFirstLoginGreeting(user, nth)`；
  - `user.setFirstLoginGreetingSent(true)` 并 save。
- `NotificationService`：
  - 新增 `sendFirstLoginGreeting(User, int nth)`：title `欢迎加入记账大王！`，content `恭喜你成为记账大王的第 {nth} 位用户！开始记录你的每一笔收支吧～`（type=WELCOME）。
  - 保留/删除 `createWelcomeNotification`：删除（逻辑已并入上述方法，避免重复）。

### 2.4 第一笔账单事件通知

- `NotificationType` 枚举增加 `EVENT`（站内信类型）。
- `RecordService` 注入 `NotificationService`：`create()` 中已有 `count = countByUserId(userId)`（保存前统计）；若 `count == 0`（首笔）：
  - 保存后调用 `notificationService.sendFirstBillNotification(user)`；
  - 需 `userRepository.findById(userId)` 取 User。
- `NotificationService.sendFirstBillNotification(User)`：title `🎉 第一笔账单已记录`，content `你刚刚记下了在记账大王的第 1 笔账单，记账好习惯从现在开始养成！`（type=EVENT）。

---

## 3. 数据 / 配置变更

- `schema.sql`：在末尾加幂等块
  ```sql
  SET @has_flg = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='user' AND COLUMN_NAME='first_login_greeting_sent');
  SET @sql_flg = IF(@has_flg=0, "ALTER TABLE user ADD COLUMN first_login_greeting_sent TINYINT(1) NOT NULL DEFAULT 0 AFTER last_login_at", 'SELECT 1');
  PREPARE stmt FROM @sql_flg; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  ```
- `application.yml`：加 `spring.jackson.time-zone: Asia/Shanghai`。
- `docker-compose.yml`：backend `JAVA_OPTS` 追加 `-Duser.timezone=Asia/Shanghai`。
- 枚举 `NotificationType` 加 `EVENT`。

---

## 4. 接口 / 前端变更

- `GET /api/game/leaderboard`：返回项新增 `me` 字段，非本人 `username` 已遮蔽。
- 前端 `SnakeGame.tsx`：用 `item.me` 判定本人；显示后端返回的用户名。
- 无新增 REST 端点；事件通知复用现有 `Notification` 表与 `myNotifications` 接口。

---

## 5. 测试计划（TDD 先写测试）

| 测试文件 | 覆盖 |
|----------|------|
| `WelcomeNotificationTest`（改写） | 注册**不再**发欢迎；首次登录发欢迎+第 N 位（断言 title 含"记账大王"、content 含"第 N 位"）；第二次登录不再发。 |
| `ScheduledNotificationTest`（改） | 设置 7:30、now=20:00 → **不触发**；now=07:31 → 触发；SPECIFIC_DATE 非当日不触发；同日不重复。 |
| `GameScoreServiceTest`（新增/扩展） | `mask`：2 字→`小**`；本人 `me=true` 且全名；他人遮蔽且 `me=false`。 |
| `NotificationServiceTest`（扩展） | `sendFirstBillNotification` 生成 EVENT 类型通知；`sendFirstLoginGreeting` 生成 WELCOME 且含 N。 |
| `RecordServiceTest`（扩展/新增） | 首笔账单创建后生成一条 EVENT 通知；第二笔不再生成。 |
| `ApiViabilityTest`（回归） | 登录后能看到欢迎/第 N 位通知；排行榜接口本人 `me=true`。 |

全量验证：`mvn test`（后端）、`npm run build`（前端）。

---

## 6. 涉及文件清单

后端：
- `entity/User.java`、`entity/NotificationType.java`
- `repository/UserRepository.java`（新增 count 方法）
- `service/AuthService.java`、`service/NotificationService.java`、`service/RecordService.java`、`service/GameScoreService.java`、`service/ScheduledNotificationDispatcher.java`
- `config/SchedulerConfig.java`
- `dto/LeaderboardEntry.java`
- `controller/GameScoreController.java`
- `resources/application.yml`、`resources/schema.sql`

前端：
- `src/types.ts`、`src/pages/SnakeGame.tsx`

部署配置：
- `docker-compose.yml`（JAVA_OPTS 时区）

---

## 7. 验收标准

1. 排行榜中他人用户名被遮蔽（如 `小**`），本人显示全名+（我），且接口返回已遮蔽。
2. 设置每天 7:30 问候：设置后**不会立即发**；服务器在上海时间 7:30~7:32 内**会发**；同日不重复。
3. 新用户注册后首次登录收到"欢迎加入记账大王！"且告知"第 N 位用户"；再次登录不再重复。
4. 用户记第一笔账单后收到"第一笔账单已记录"事件通知；后续账单不再发。
5. 后端 `mvn test` 全绿，前端 `npm run build` 成功。
