package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyStats {
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal balance;
}
