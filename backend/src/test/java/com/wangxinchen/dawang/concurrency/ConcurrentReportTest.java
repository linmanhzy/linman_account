package com.wangxinchen.dawang.concurrency;

import com.wangxinchen.dawang.AccountApplication;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 并发报表/余额查询正确性：在「持续写入」背景下并发查询月度报表/趋势/分类占比，
 * 断言所有读请求不崩（code==0），且月报始终满足 余额 == 收入 - 支出（无脏读/算术错乱）。
 */
@SpringBootTest(classes = AccountApplication.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("concurrency")
class ConcurrentReportTest extends ConcurrencyTestSupport {

    private static final int THREADS = 10;
    private static final int PER_THREAD = 20;

    private String userToken;
    private String month;

    @BeforeAll
    void setup() throws Exception {
        Auth auth = register("ctrep_" + System.nanoTime(), "Report@123456");
        this.userToken = auth.token();
        this.month = YearMonth.now().toString();
        // 预置一批数据，让报表有内容可查
        String date = LocalDate.now().toString();
        for (int i = 0; i < 30; i++) {
            boolean income = i % 3 != 0;
            String body = "{\"type\":\"" + (income ? "income" : "expense") + "\",\"amount\":\""
                    + (income ? "20.00" : "8.00") + "\",\"recordDate\":\"" + date
                    + "\",\"categoryL1\":\"测试\",\"categoryL2\":\"并发\",\"note\":\"seed\"}";
            exec(post("/api/records").header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }

    @Test
    void concurrentReads_whileWriting_noCrash_andBalanceInvariant() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean invariantBroken = new AtomicBoolean(false);
        AtomicInteger readerSeq = new AtomicInteger();
        String date = LocalDate.now().toString();

        // 后台持续写入线程
        Thread writer = new Thread(() -> {
            int n = 0;
            while (running.get()) {
                boolean income = (n++ % 2 == 0);
                String body = "{\"type\":\"" + (income ? "income" : "expense") + "\",\"amount\":\"1.00\","
                        + "\"recordDate\":\"" + date + "\",\"categoryL1\":\"测试\",\"categoryL2\":\"并发\"}";
                exec(post("/api/records").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body));
            }
        });
        writer.start();

        Callable<Raw> reader = () -> {
            int k = readerSeq.getAndIncrement();
            String auth = "Bearer " + userToken;
            Raw r;
            if (k % 3 == 0) {
                r = exec(get("/api/records/stats/monthly").param("month", month).header("Authorization", auth));
                // 校验余额不变量：balance == income - expense，否则标记破坏
                if (r.ok()) {
                    JsonNode d = objectMapper.readTree(r.body()).path("data");
                    BigDecimal bal = new BigDecimal(d.path("balance").asText());
                    BigDecimal inc = new BigDecimal(d.path("income").asText());
                    BigDecimal exp = new BigDecimal(d.path("expense").asText());
                    if (bal.compareTo(inc.subtract(exp)) != 0) {
                        invariantBroken.set(true);
                        r = new Raw(r.status(), false, r.body());
                    }
                }
            } else if (k % 3 == 1) {
                r = exec(get("/api/reports/trend").param("months", "12").header("Authorization", auth));
            } else {
                String type = (k % 2 == 0) ? "income" : "expense";
                r = exec(get("/api/reports/category-proportion").param("type", type).header("Authorization", auth));
            }
            return r;
        };

        long start = System.currentTimeMillis();
        List<Result> results = fireConcurrently(THREADS, PER_THREAD, reader);
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        running.set(false);
        writer.join();
        ConcurrencySummary s = summarize(results, secs);
        System.out.println("[并发报表] " + s);

        assertTrue(results.stream().allMatch(Result::ok), "并发报表查询存在失败/异常");
        assertEquals(0.0, s.errorRatePct(), 0.0001, "并发报表错误率应=0");
        assertFalse(invariantBroken.get(), "并发读写下月报余额不满足 余额==收入-支出");
    }
}
