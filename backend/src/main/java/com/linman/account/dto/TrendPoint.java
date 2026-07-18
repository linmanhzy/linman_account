package com.linman.account.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 消费趋势的一个月数据点：某月收入与支出。
 * month 形如 "2026-07"。
 */
@Data
public class TrendPoint {
    private String month;
    private BigDecimal income;
    private BigDecimal expense;
}
