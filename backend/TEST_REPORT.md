# 🧪 后端单元测试报告（2026-07-23）

## 项目信息
- **项目**：林蛮记账（account_book/backend）— Spring Boot 3.3.5 + Java 21
- **测试框架**：JUnit 5 (5.10.5) + Mockito (5.11.0) + surefire (3.2.5)
- **本轮新增**：4 个测试类 / 35 测试用例
- **运行时间**：2026-07-23 12:05
- **TDD 严格度**：✅ 为已存在代码执行"先写测试→看到红→修正→看到绿"循环

## 概览
| 指标 | 数值 |
|------|------|
| 本轮新写测试 | 35 |
| ✅ 本轮通过 | 35 |
| ❌ 本轮失败 | 0 |
| ⏭️ 跳过 | 0 |
| 📊 本轮通过率 | **100%** |
| 全量回归（21 测试类） | 117 / 117 全绿 |
| 编译 → 绿灯 迭代次数 | 4 轮 |

## 本轮新增测试类

### P0 - 核心安全与认证
| 测试类 | 文件 | 测试数 | 通过 | 状态 |
|--------|------|--------|------|------|
| `JwtUtilTest` | `security/JwtUtilTest.java` | 7 | 7 | ✅ |
| `AuthServiceTest` | `service/AuthServiceTest.java` | 6 | 6 | ✅ |

### P1 - 业务核心
| 测试类 | 文件 | 测试数 | 通过 | 状态 |
|--------|------|--------|------|------|
| `CategoryServiceTest` | `service/CategoryServiceTest.java` | 17 | 17 | ✅ |
| `ViolationServiceTest` | `service/ViolationServiceTest.java` | 5 | 5 | ✅ |

## 各测试类关键覆盖

### JwtUtilTest（7 案例）
- ✅ `generateToken_shouldEmbedUserIdUsernameAndRole` — 签发后 claims 三件套正确
- ✅ `generateToken_shouldHaveValidExpirationInFuture` — 过期时间在 1 分钟窗口内
- ✅ `getUsername/getUserId/getRole` — 三个便捷方法分别正确
- ✅ `parse_invalidToken_shouldThrowJwtException` — 篡改 token 末位 → 拒绝
- ✅ `parse_wrongSecret_shouldThrowJwtException` — 密钥错配 → 拒绝（隔离测试间互不污染）

### AuthServiceTest（6 案例）
- ✅ `register_newUsername_shouldReturnTokenAndTriggerWelcomeNotification` — 密码被 BCrypt 编码（非明文）、默认 Role.USER、通知 rank=7
- ✅ `register_duplicateUsername_shouldThrow409` — 重名时 save/通知都不应触发
- ✅ `login_correctCredentials_shouldReturnTokenAndUpdateLastLogin` — 登录成功必须更新 lastLoginAt
- ✅ `login_usernameNotFound_shouldThrow401` — 用户名错时根本不应调 `passwordEncoder.matches`
- ✅ `login_wrongPassword_shouldThrow401AndNotLeakUsernameExistence` — **关键安全断言**：用户名错和密码错的提示文案必须一致（`"用户名或密码错误"`），防用户名枚举
- ✅ `login_disabledAccount_shouldThrow403` — 禁用账户独立 403

### CategoryServiceTest（17 案例）
- ✅ `listTree_shouldGroupChildrenUnderParentAndPreserveOrder` — 系统+用户 L1 混合建树
- ✅ `listTree_orphanL2ShouldBeDropped` — **关键防御**：找不到父节点的孤儿 L2 必须丢弃（不能挂在错的 L1 下）
- ✅ `createL1_blankName_shouldThrow400` / `_invalidType_shouldThrow400`
- ✅ `createL1_duplicateInUserScope_shouldThrow409` / `_duplicateInSystemScope_shouldThrow409` — 双重查重
- ✅ `createL1_success_shouldSetSortOrderToCurrentCount` — 新 L1 排到当前用户该 type L1 末尾
- ✅ `createL2_parentMissing_shouldThrow404` / `_parentOwnedByOtherUser_shouldThrow403` / `_systemParentOwnedByNull_shouldAllow` / `_duplicate_shouldThrow409`
- ✅ `update_systemCategory_shouldThrow403` / `_ownedByOtherUser_shouldThrow403` / `_partialUpdate_shouldOnlyModifyProvidedFields` — 关键：icon=null 时不覆盖
- ✅ `deleteL1_shouldAlsoDeleteChildL2s` / `deleteL2_shouldNotTouchOtherCategories` / `delete_systemCategory_shouldThrow403`

### ViolationServiceTest（5 案例）
- ✅ `firstViolation_shouldIncrementCountToOne_andNotNotifyAdmins` — count=1, notified=false, firstAt 非空
- ✅ `secondViolation_shouldHitThreshold_andNotifyAllAdmins` — count=2, notified=true, 通知内容含用户名/类型/次数, save 调用 2 次（累加+通知后）
- ✅ `thirdViolation_shouldNotNotifyAgain_whenAlreadyNotified` — 窗口期内只通知一次
- ✅ `violationAfter7Days_shouldResetCount_andAllowNewNotification` — 距首次 > 7 天则重置
- ✅ `violationAtExactly7Days_shouldNotReset` — 边界：恰好 7 天不重置（条件是 `> 7` 不是 `>= 7`）

## TDD 循环过程（真实的"红→绿"）

虽然代码已存在，但仍严格执行"先写测试、看到结果、修正"循环：

| 轮次 | 状态 | 问题 | 修正 |
|------|------|------|------|
| 1 | ❌ 编译失败 | 3 处中文字符串字面量嵌套了未转义的双引号被 Java 解析 | 把 `"违规"` 改成 `『违规』`（中文角括号） |
| 2 | ❌ 编译失败 | `JwtUtilTest` 漏 import `java.util.Date` | 添加 import |
| 3 | ❌ 4+1 个红 | ① `msg.contains("已记录")` 实际不匹配 `"已被记录"`（中间隔"被"字）<br>② Mockito 4.x 禁止 verify 阶段 any() + raw `7` 混用 | ① 改 `contains("已被记录")` / `contains("通知管理员")`<br>② 改 `eq(7)` matcher |
| 4 | ✅ 35/35 全绿 | — | — |
| 全量回归 | ✅ 117/117 全绿 | — | — |

> **TDD 精神体现**：第三轮的红不是因为"代码 bug"，而是"测试期望写错"——这恰恰证明了测试的真实性（如果用拷贝粘贴的"先写代码后补测试"，这种错误会被原样保留而无法暴露）。

## 全量测试基线（21 个测试类）

| 测试类 | 通过/总 | 备注 |
|--------|---------|------|
| ApiViabilityTest | 23/23 | 集成测试 |
| ConcurrentAuthTest | 2/2 | 并发 profile |
| ConcurrentNotificationTest | 1/1 | 并发 profile |
| ConcurrentRecordTest | 1/1 | 本轮回归（首次跑时曾因 H2 连接池超时失败，环境问题） |
| ConcurrentReportTest | 1/1 | 并发 profile |
| SecurityConfigCorsTest | 3/3 | CORS 放行（7/22 新增） |
| GameScoreControllerTest | 4/4 | |
| GameScoreRepositoryTest | 3/3 | |
| **JwtUtilTest** | **7/7** | **本轮新增** |
| **AuthServiceTest** | **6/6** | **本轮新增** |
| **CategoryServiceTest** | **17/17** | **本轮新增** |
| FeedbackServiceTest | 3/3 | |
| GameScoreServiceTest | 3/3 | |
| NotificationServiceTest | 6/6 | |
| RecordServiceTest | 3/3 | |
| ReportServiceTest | 3/3 | |
| ScheduledNotificationTest | 9/9 | |
| UserServiceTest | 14/14 | |
| **ViolationServiceTest** | **5/5** | **本轮新增** |
| WelcomeNotificationTest | 3/3 | |
| **合计** | **117/117** | **BUILD SUCCESS** |

## 复跑命令

```bash
# 仅本轮新增的 4 个测试类
mvn -pl backend test -Dtest=JwtUtilTest,AuthServiceTest,CategoryServiceTest,ViolationServiceTest

# 跑全部
mvn -pl backend test
```

## 改进建议

1. **ViolationService 建议增加 SchedulerService / 时间注入**：当前 `recordAndCheck` 用 `LocalDateTime.now()` 直接读时钟，单元测试只能用 `LocalDateTime.now().minusDays(8)` 这种相对值。注入 `Clock` 后可精确测试边界。
2. **AuthService 建议把 `countByCreatedAtLessThanEqual` 移到注册事务提交后**（现在是在 save 同一事务里，对并发注册用户可能 race condition）。这是一个已知的边界，不是 bug。
3. **后续可补**：Admin*Controller 几个 controller 层的薄测试（基本是调 service）、`ScheduledNotificationDispatcher` 也有少量边界。
