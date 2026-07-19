package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.CategoryProportion;
import com.wangxinchen.dawang.dto.TrendPoint;
import com.wangxinchen.dawang.entity.Record;
import com.wangxinchen.dawang.repository.RecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private RecordRepository recordRepository;

    @InjectMocks
    private ReportService reportService;

    private Record rec(Long userId, String type, BigDecimal amount,
                       int year, int month, int day, String category) {
        Record r = new Record();
        r.setUserId(userId);
        r.setType(type);
        r.setAmount(amount);
        r.setRecordDate(LocalDate.of(year, month, day));
        r.setCategoryL1(category);
        return r;
    }

    @Test
    void trend_zeroFillsMonthsAndSumsByType() {
        Long uid = 1L;
        LocalDate now = LocalDate.now();
        Record income = rec(uid, "income", new BigDecimal("1000"),
                now.getYear(), now.getMonthValue(), 5, "工资");
        Record expense = rec(uid, "expense", new BigDecimal("300"),
                now.getYear(), now.getMonthValue(), 6, "餐饮");
        when(recordRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(eq(uid), any(), any()))
                .thenReturn(List.of(income, expense));

        List<TrendPoint> trend = reportService.monthlyTrend(uid, 3);

        assertEquals(3, trend.size());
        TrendPoint current = trend.get(trend.size() - 1);
        assertEquals(0, new BigDecimal("1000").compareTo(current.getIncome()));
        assertEquals(0, new BigDecimal("300").compareTo(current.getExpense()));
        for (int i = 0; i < trend.size() - 1; i++) {
            assertEquals(BigDecimal.ZERO, trend.get(i).getIncome());
            assertEquals(BigDecimal.ZERO, trend.get(i).getExpense());
        }
    }

    @Test
    void categoryProportion_computesPercentageAndSorts() {
        Long uid = 2L;
        Record r1 = rec(uid, "expense", new BigDecimal("70"), 2026, 1, 1, "餐饮");
        Record r2 = rec(uid, "expense", new BigDecimal("30"), 2026, 1, 2, "交通");
        when(recordRepository.findByUserIdAndTypeOrderByRecordDateAscIdAsc(uid, "expense"))
                .thenReturn(List.of(r1, r2));

        List<CategoryProportion> props = reportService.categoryProportion(uid, "expense", null);

        assertEquals(2, props.size());
        assertEquals("餐饮", props.get(0).getCategory());
        assertEquals(0, new BigDecimal("70").compareTo(props.get(0).getAmount()));
        assertEquals(0, new BigDecimal("70.00").compareTo(props.get(0).getPercentage()));
        assertEquals("交通", props.get(1).getCategory());
        assertEquals(0, new BigDecimal("30.00").compareTo(props.get(1).getPercentage()));
    }

    @Test
    void categoryProportion_handlesEmptyTotal() {
        Long uid = 3L;
        when(recordRepository.findByUserIdAndTypeOrderByRecordDateAscIdAsc(uid, "expense"))
                .thenReturn(List.of());
        List<CategoryProportion> props = reportService.categoryProportion(uid, "expense", null);
        assertTrue(props.isEmpty());
    }
}
