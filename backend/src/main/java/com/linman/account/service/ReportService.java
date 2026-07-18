package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.CategoryProportion;
import com.linman.account.dto.TrendPoint;
import com.linman.account.entity.Record;
import com.linman.account.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表统计：消费趋势（按月）、分类占比。
 * 只统计「当前登录用户」的数据，数据隔离在服务器侧强制完成。
 */
@Service
public class ReportService {
    private final RecordRepository recordRepository;

    public ReportService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * 最近 N 个月的每月收入/支出。N 越界时默认 12。
     * 即使某个月没有任何记录，也会返回 0 值点，保证折线图连续。
     */
    public List<TrendPoint> monthlyTrend(Long userId, int months) {
        if (months <= 0 || months > 60) {
            months = 12;
        }
        YearMonth nowYm = YearMonth.from(LocalDate.now());
        YearMonth startYm = nowYm.minusMonths(months - 1);
        LocalDate start = startYm.atDay(1);

        List<Record> records = recordRepository
                .findByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(userId, start, LocalDate.now());

        // 预生成连续月份序列，避免月中出现断点
        List<TrendPoint> points = new ArrayList<>();
        Map<String, TrendPoint> index = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = startYm.plusMonths(i);
            String key = ym.toString(); // YYYY-MM
            TrendPoint p = new TrendPoint();
            p.setMonth(key);
            p.setIncome(BigDecimal.ZERO);
            p.setExpense(BigDecimal.ZERO);
            points.add(p);
            index.put(key, p);
        }

        for (Record r : records) {
            TrendPoint p = index.get(YearMonth.from(r.getRecordDate()).toString());
            if (p == null) {
                continue;
            }
            if ("income".equals(r.getType())) {
                p.setIncome(p.getIncome().add(r.getAmount()));
            } else if ("expense".equals(r.getType())) {
                p.setExpense(p.getExpense().add(r.getAmount()));
            }
        }
        return points;
    }

    /**
     * 按一级分类统计某类型（默认支出）的占比。
     * month 为空表示统计全部时间；否则只统计该月。
     */
    public List<CategoryProportion> categoryProportion(Long userId, String type, String month) {
        if (type == null || type.isBlank()) {
            type = "expense";
        }
        List<Record> records;
        if (month != null && !month.isBlank()) {
            LocalDate start = LocalDate.parse(month + "-01");
            LocalDate end = start.plusMonths(1).minusDays(1);
            records = recordRepository
                    .findByUserIdAndTypeAndRecordDateBetweenOrderByRecordDateAscIdAsc(userId, type, start, end);
        } else {
            records = recordRepository.findByUserIdAndTypeOrderByRecordDateAscIdAsc(userId, type);
        }

        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Record r : records) {
            String cat = (r.getCategoryL1() == null || r.getCategoryL1().isBlank()) ? "未分类" : r.getCategoryL1();
            sums.merge(cat, r.getAmount(), BigDecimal::add);
            total = total.add(r.getAmount());
        }

        List<CategoryProportion> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : sums.entrySet()) {
            CategoryProportion cp = new CategoryProportion();
            cp.setCategory(e.getKey());
            cp.setAmount(e.getValue());
            BigDecimal pct = total.signum() == 0
                    ? BigDecimal.ZERO
                    : e.getValue().multiply(new BigDecimal("100"))
                    .divide(total, 2, RoundingMode.HALF_UP);
            cp.setPercentage(pct);
            result.add(cp);
        }
        // 金额从大到小排列，饼图更直观
        result.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));
        return result;
    }
}
