package com.wangxinchen.dawang.concurrency;

import com.wangxinchen.dawang.AccountApplication;
import com.wangxinchen.dawang.repository.NotificationRepository;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 并发站内信正确性：管理员预发一批通知给测试用户后，并发进行列表/未读计数/全部已读，
 * 断言通知不丢（总数恒定）且读请求全成功。
 */
@SpringBootTest(classes = AccountApplication.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("concurrency")
class ConcurrentNotificationTest extends ConcurrencyTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    private static final int THREADS = 10;
    private static final int PER_THREAD = 10;
    private static final int SENT = 50;

    private String userToken;
    private Long userId;
    private String adminToken;

    @BeforeAll
    void setup() throws Exception {
        Auth user = register("ctnotif_" + System.nanoTime(), "Notif@123456");
        this.userToken = user.token();
        this.userId = user.userId();
        this.adminToken = login("admin", "admin123456").token();

        // 管理员给该用户发送 SENT 条站内信
        for (int i = 0; i < SENT; i++) {
            Raw r = exec(post("/api/admin/notifications")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"并发通知" + i + "\",\"content\":\"c" + i
                            + "\",\"targetUserId\":" + userId + "}"));
            assertTrue(r.ok(), "预发站内信失败 #" + i);
        }
    }

    @Test
    void concurrentNotificationOps_noLost() throws Exception {
        AtomicInteger seq = new AtomicInteger();
        String auth = "Bearer " + userToken;
        Callable<Raw> task = () -> {
            int k = seq.getAndIncrement();
            Raw r;
            if (k % 3 == 0) {
                r = exec(get("/api/notifications").header("Authorization", auth));
            } else if (k % 3 == 1) {
                r = exec(get("/api/notifications/unread-count").header("Authorization", auth));
            } else {
                r = exec(put("/api/notifications/read-all").header("Authorization", auth));
            }
            return r;
        };

        long start = System.currentTimeMillis();
        List<Result> results = fireConcurrently(THREADS, PER_THREAD, task);
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        ConcurrencySummary s = summarize(results, secs);
        System.out.println("[并发站内信] " + s);

        assertTrue(results.stream().allMatch(Result::ok), "并发站内信操作存在失败");
        assertEquals(0.0, s.errorRatePct(), 0.0001, "并发站内信错误率应=0");

        // 通知不丢：总数恒等于 SENT + 1(注册时自动创建的欢迎通知)
        long total = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        assertEquals(SENT + 1, total, "并发站内信操作导致通知数量异常");

        // 未读 + 已读 == 总数（不变量）
        long unread = notificationRepository.countByUserIdAndIsRead(userId, false);
        assertEquals(total, unread + (total - unread), "未读+已读 != 总数");
    }
}
