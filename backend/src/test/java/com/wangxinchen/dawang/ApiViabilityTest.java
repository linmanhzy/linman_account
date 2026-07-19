package com.wangxinchen.dawang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后端接口可用性集成测试：扫描全部 Controller，逐接口验证「真正可用」。
 * 覆盖：公开接口（无 token）、受保护接口（带 admin token，含完整 CRUD 链路）、鉴权反向用例（401/403）。
 * 启动完整 Spring 上下文 + H2，JWT 过滤器与 Spring Security 真实生效。
 */
@SpringBootTest(classes = AccountApplication.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiViabilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private Long adminId;
    private String authHeader() {
        return "Bearer " + adminToken;
    }

    /** 登录助手：在所有用例前以默认 admin 登录，拿到 JWT 与 userId，供受保护接口复用。 */
    @BeforeAll
    void loginAsAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        adminToken = node.path("data").path("token").asText();
        adminId = node.path("data").path("userId").asLong();
        if (adminToken == null || adminToken.isBlank()) {
            throw new IllegalStateException("登录未返回 token，无法继续受保护接口测试");
        }
    }

    // ============ 公开接口（无 token） ============
    @Test
    void health_public_returns200() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void login_public_returnsToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    // ============ 鉴权反向用例 ============
    // 无 token 访问 @PreAuthorize 接口：匿名用户被授权层拒绝，返回 403（接口未“裸奔”）
    @Test
    void adminUsers_withoutToken_returns403() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    // 带伪造/无效 token：JwtAuthenticationFilter 直接返回 401（走过滤器而非授权层）
    @Test
    void adminUsers_withInvalidToken_returns401() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    // ============ AdminUserController ============
    @Test
    void listUsers_withAdminToken_returns200() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============ CategoryController ============
    @Test
    void categories_tree_withToken_returns200() throws Exception {
        mvc.perform(get("/api/categories").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void categories_createAndDelete_withToken_returns200() throws Exception {
        String name = " viability_" + System.nanoTime();
        MvcResult create = mvc.perform(post("/api/categories")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"expense\",\"icon\":\"🍔\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(create.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(delete("/api/categories/" + id).header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ============ RecordController（完整 CRUD 链路） ============
    @Test
    void records_crud_withToken_returns200() throws Exception {
        String createBody = "{\"type\":\"expense\",\"amount\":10.50,\"recordDate\":\"2026-07-18\","
                + "\"categoryL1\":\"餐饮\",\"categoryL2\":\"午餐\",\"note\":\"viability-test\"}";
        MvcResult create = mvc.perform(post("/api/records")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(create.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(get("/api/records").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        String updateBody = "{\"type\":\"expense\",\"amount\":20.00,\"recordDate\":\"2026-07-18\",\"note\":\"updated\"}";
        mvc.perform(put("/api/records/" + id)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(delete("/api/records/" + id).header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void records_monthlyStats_withToken_returns200() throws Exception {
        mvc.perform(get("/api/records/stats/monthly").param("month", "2026-07")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void records_exportCsv_withToken_returns200() throws Exception {
        mvc.perform(get("/api/records/export").param("format", "csv")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }

    // ============ ReportController ============
    @Test
    void reports_trend_withToken_returns200() throws Exception {
        mvc.perform(get("/api/reports/trend").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void reports_categoryProportion_withToken_returns200() throws Exception {
        mvc.perform(get("/api/reports/category-proportion").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============ FeedbackController ============
    @Test
    void feedback_submitAndMy_withToken_returns200() throws Exception {
        MvcResult submit = mvc.perform(post("/api/feedback")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"viability-test feedback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(submit.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(get("/api/feedback/my").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============ AdminFeedbackController ============
    @Test
    void adminFeedback_list_withAdminToken_returns200() throws Exception {
        mvc.perform(get("/api/admin/feedback").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void adminFeedback_reply_withAdminToken_returns200() throws Exception {
        // 先以普通用户身份提交一条反馈，再以管理员身份回复，验证完整链路
        MvcResult submit = mvc.perform(post("/api/feedback")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"feedback-to-reply\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(submit.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(put("/api/admin/feedback/" + id + "/reply")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"已收到\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ============ NotificationController ============
    @Test
    void notifications_listAndUnread_withToken_returns200() throws Exception {
        mvc.perform(get("/api/notifications").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void notifications_markReadAndReadAll_withToken_returns200() throws Exception {
        // 管理员发一条「指定给 admin 自己」的通知，拿到通知 id 后再标记已读，验证完整链路
        // 注：广播(sendToAll)依赖库中存在 USER 角色用户，测试 H2 库仅 admin，故此处发给指定用户
        mvc.perform(post("/api/admin/notifications")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"viability\",\"content\":\"direct test\",\"targetUserId\":" + adminId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult list = mvc.perform(get("/api/notifications").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(list.getResponse().getContentAsString()).path("data");
        if (arr.isArray() && arr.size() > 0) {
            long nid = arr.get(0).path("id").asLong();
            mvc.perform(put("/api/notifications/" + nid + "/read").header("Authorization", authHeader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        mvc.perform(put("/api/notifications/read-all").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ============ AdminNotificationController ============
    @Test
    void adminNotifications_send_withAdminToken_returns200() throws Exception {
        mvc.perform(post("/api/admin/notifications")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"viability\",\"content\":\"send test\",\"targetUserId\":" + adminId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // 广播（无 targetUserId）：测试 H2 库无 USER 角色用户，验证修复后静默成功（影响 0 人）而非 400
    @Test
    void adminNotifications_broadcast_withNoUsers_returns200() throws Exception {
        mvc.perform(post("/api/admin/notifications")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"viability\",\"content\":\"broadcast test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ============ GameScoreController ============
    @Test
    void game_submitAndQuery_withToken_returns200() throws Exception {
        mvc.perform(post("/api/game/scores")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists());

        mvc.perform(get("/api/game/my").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bestScore").exists());

        mvc.perform(get("/api/game/leaderboard").header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }
}
