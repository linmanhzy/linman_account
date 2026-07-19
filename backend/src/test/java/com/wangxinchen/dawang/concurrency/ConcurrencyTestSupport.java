package com.wangxinchen.dawang.concurrency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 并发测试公共底座：封装「线程池 + CyclicBarrier 统一起跑 + CountDownLatch 等齐」的并发发射、
 * 单次请求计时、以及吞吐/延迟/错误率汇总。具体用例类自行加
 * @SpringBootTest / @AutoConfigureMockMvc / @TestInstance(PER_CLASS) / @ActiveProfiles("concurrency")。
 *
 * 设计说明：MockMvc 直连 Spring 上下文（经真实 Security 过滤器链，但不走 Tomcat/网络），
 * 因此适合验证「数据正确性」（可直接查库断言）；真实 HTTP 并发压测见 tests/loadtest（Locust）。
 */
public abstract class ConcurrencyTestSupport {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;

    /** 单次请求结果（不含耗时，耗时由 fireConcurrently 测量）。 */
    protected record Raw(int status, boolean ok, String body) {}

    /** 带耗时的完整结果。 */
    protected record Result(long latencyMs, int status, boolean ok, String body) {}

    /** 登录/注册得到的身份。 */
    protected record Auth(String token, Long userId) {}

    /** 并发汇总指标。 */
    protected record ConcurrencySummary(long total, long success, double throughputPerSec,
                                         long avgMs, long maxMs, double errorRatePct) {
        @Override
        public String toString() {
            return String.format("总请求=%d 成功=%d 吞吐=%.1f/s 平均=%dms 最大=%dms 错误率=%.2f%%",
                    total, success, throughputPerSec, avgMs, maxMs, errorRatePct);
        }
    }

    /**
     * 用 threads 个线程、每线程 perThread 次，CyclicBarrier 统一放行模拟并发峰值。
     * task 内部执行一次请求并返回 Raw；本方法负责计时、异常捕获与结果收集。
     */
    protected List<Result> fireConcurrently(int threads, int perThread, Callable<Raw> task)
            throws InterruptedException {
        int total = threads * perThread;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(total);
        List<Result> results = new CopyOnWriteArrayList<>();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    barrier.await(); // 所有 worker 同时起跑，模拟并发洪峰
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    latch.countDown();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    long start = System.nanoTime();
                    int status = -1;
                    boolean ok = false;
                    String body = "";
                    try {
                        Raw r = task.call();
                        status = r.status();
                        ok = r.ok();
                        body = r.body();
                    } catch (Exception e) {
                        status = -1;
                        ok = false;
                        body = "EXCEPTION: " + e.getMessage();
                    }
                    long latencyMs = (System.nanoTime() - start) / 1_000_000;
                    results.add(new Result(latencyMs, status, ok, body));
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new ArrayList<>(results);
    }

    protected ConcurrencySummary summarize(List<Result> results, double wallClockSeconds) {
        long total = results.size();
        long success = results.stream().filter(Result::ok).count();
        long max = results.stream().mapToLong(Result::latencyMs).max().orElse(0);
        double avg = results.stream().mapToLong(Result::latencyMs).average().orElse(0);
        double err = total == 0 ? 0 : (total - success) * 100.0 / total;
        double tput = wallClockSeconds <= 0 ? 0 : total / wallClockSeconds;
        return new ConcurrencySummary(total, success, tput, (long) avg, max, err);
    }

    /** 执行一次 MockMvc 请求，返回状态码 / 是否业务成功(code==0) / 响应体。 */
    protected Raw exec(RequestBuilder builder) {
        try {
            MvcResult r = mvc.perform(builder).andReturn();
            int status = r.getResponse().getStatus();
            String body = r.getResponse().getContentAsString();
            boolean ok = status == 200 && objectMapper.readTree(body).path("code").asInt() == 0;
            return new Raw(status, ok, body);
        } catch (Exception e) {
            return new Raw(-1, false, "EXCEPTION: " + e.getMessage());
        }
    }

    /** 注册一个新用户并返回其 JWT 与 userId。 */
    protected Auth register(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        if (node.path("code").asInt() != 0) {
            throw new IllegalStateException("注册失败 " + username + ": " + node);
        }
        return new Auth(node.path("data").path("token").asText(),
                node.path("data").path("userId").asLong());
    }

    /** 登录并返回其 JWT 与 userId。 */
    protected Auth login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        if (node.path("code").asInt() != 0) {
            throw new IllegalStateException("登录失败 " + username + ": " + node);
        }
        return new Auth(node.path("data").path("token").asText(),
                node.path("data").path("userId").asLong());
    }
}
