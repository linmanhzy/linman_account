package com.wangxinchen.dawang.concurrency;

import com.wangxinchen.dawang.AccountApplication;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 并发登录 + 并发注册正确性：并发登录全部成功拿 token；并发注册不同用户名均落库、可登录（无串号/碰撞）。
 */
@SpringBootTest(classes = AccountApplication.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("concurrency")
class ConcurrentAuthTest extends ConcurrencyTestSupport {

    private static final int THREADS = 10;
    private static final int PER_THREAD = 10;

    @Test
    void concurrentLogin_allSucceed() throws Exception {
        Callable<Raw> task = () -> exec(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123456\"}"));

        long start = System.currentTimeMillis();
        List<Result> results = fireConcurrently(THREADS, PER_THREAD, task);
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        ConcurrencySummary s = summarize(results, secs);
        System.out.println("[并发登录] " + s);

        assertTrue(results.stream().allMatch(r -> r.ok() && !r.body().isEmpty()), "并发登录存在失败");
        assertEquals(0.0, s.errorRatePct(), 0.0001, "并发登录错误率应=0");
    }

    @Test
    void concurrentRegister_uniqueUsers_allPersisted() throws Exception {
        CopyOnWriteArrayList<String> registered = new CopyOnWriteArrayList<>();
        AtomicInteger seq = new AtomicInteger();
        Callable<Raw> task = () -> {
            String u = "ctauth_" + seq.getAndIncrement() + "_" + System.nanoTime();
            Raw r = exec(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + u + "\",\"password\":\"Auth@123456\"}"));
            if (r.ok()) {
                registered.add(u);
            }
            return r;
        };

        long start = System.currentTimeMillis();
        List<Result> results = fireConcurrently(THREADS, PER_THREAD, task);
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        ConcurrencySummary s = summarize(results, secs);
        System.out.println("[并发注册] " + s);

        assertTrue(results.stream().allMatch(Result::ok), "并发注册存在失败");
        assertEquals(0.0, s.errorRatePct(), 0.0001, "并发注册错误率应=0");

        // 注册成功的用户都能登录（无重复用户名串号 / 均落库）
        for (String u : registered) {
            Raw login = exec(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + u + "\",\"password\":\"Auth@123456\"}"));
            assertTrue(login.ok(), "并发注册的用户无法登录（疑似串号/丢失）：" + u);
        }
    }
}
