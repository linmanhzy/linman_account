package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 某一分类的占比：金额 + 占总支出的百分比（0~100）。
 */
@Data
public class CategoryProportion {
    private String category;
    private BigDecimal amount;
    private BigDecimal percentage;
}
