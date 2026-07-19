package com.wangxinchen.dawang.concurrency;

import com.wangxinchen.dawang.AccountApplication;
import com.wangxinchen.dawang.entity.Record;
import java.util.concurrent.Callable;
import com.wangxinchen.dawang.repository.RecordRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 并发记账正确性 + 性能：多“用户线程”同时给同一用户新增记录，
 * 断言「记录不丢」且「余额恒等于 收入-支出」（接口值 == 数据库重算值）。
 */
@SpringBootTest(classes = AccountApplication.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("concurrency")
class ConcurrentRecordTest extends ConcurrencyTestSupport {

    @Autowired
    private RecordRepository recordRepository;

    private static final int THREADS = 10;
    private static final int PER_THREAD = 20; // 每线程 20 条：10 income@10 + 10 expense@5

    private String userToken;
    private Long userId;
    private String month;
    private BigDecimal expectedBalance;

    @BeforeAll
    void setup() throws Exception {
        String username = "ctrec_" + System.nanoTime();
        Auth auth = register(username, "Record@123456");
        this.userToken = auth.token();
        this.userId = auth.userId();
        this.month = YearMonth.now().toString();
    }

    @Test
    void concurrentCreate_recordsNotLost_andBalanceHolds() throws Exception {
        AtomicInteger seq = new AtomicInteger();
        String date = LocalDate.now().toString();
        Callable<Raw> task = () -> {
            int i = seq.getAndIncrement();
            boolean income = i % 2 == 0;
            String type = income ? "income" : "expense";
            String amount = income ? "10.00" : "5.00";
            String body = "{\"type\":\"" + type + "\",\"amount\":" + amount
                    + ",\"recordDate\":\"" + date + "\",\"categoryL1\":\"测试\",\"categoryL2\":\"并发\",\"note\":\"ct\"}";
            return exec(post("/api/records")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(body));
        };

        long start = System.currentTimeMillis();
        List<Result> results = fireConcurrently(THREADS, PER_THREAD, task);
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        ConcurrencySummary s = summarize(results, secs);
        System.out.println("[并发记账] " + s);

        // 1) 全部请求成功，零错误
        assertTrue(results.stream().allMatch(Result::ok), "并发记账存在失败请求");
        assertEquals(0.0, s.errorRatePct(), 0.0001, "并发记账错误率应=0");

        // 2) 记录不丢：总记录数 == 成功请求数
        long expectedTotal = (long) THREADS * PER_THREAD;
        long actual = recordRepository.countByUserId(userId);
        assertEquals(expectedTotal, actual, "并发记账出现记录丢失");

        // 3) 余额恒等：一半 income@10 + 一半 expense@5 => 净 = total/2 * 5
        expectedBalance = BigDecimal.valueOf(expectedTotal / 2).multiply(BigDecimal.valueOf(5));

        Raw stats = exec(get("/api/records/stats/monthly").param("month", month)
                .header("Authorization", "Bearer " + userToken));
        assertTrue(stats.ok(), "月报查询失败");
        BigDecimal apiBalance = new BigDecimal(objectMapper.readTree(stats.body())
                .path("data").path("balance").asText());

        BigDecimal dbBalance = recomputeBalance();
        assertEquals(0, expectedBalance.compareTo(apiBalance), "API 余额 != 收入-支出 预期值");
        assertEquals(0, dbBalance.compareTo(apiBalance), "数据库重算余额与 API 不一致");
    }

    /** 从数据库重算：sum(income) - sum(expense)。 */
    private BigDecimal recomputeBalance() {
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (Record r : recordRepository.findByUserIdOrderByRecordDateDescIdDesc(userId)) {
            if ("income".equals(r.getType())) {
                income = income.add(r.getAmount());
            } else if ("expense".equals(r.getType())) {
                expense = expense.add(r.getAmount());
            }
        }
        return income.subtract(expense);
    }
}
