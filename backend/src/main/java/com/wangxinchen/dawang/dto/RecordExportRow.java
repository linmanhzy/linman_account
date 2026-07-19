package com.wangxinchen.dawang.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导出 Excel 时的每一行。用中文表头，类型转成「收入/支出」更直观。
 */
@Data
public class RecordExportRow {
    @ExcelProperty("类型")
    private String typeLabel;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty("日期")
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate recordDate;

    @ExcelProperty("一级分类")
    private String categoryL1;

    @ExcelProperty("二级分类")
    private String categoryL2;

    @ExcelProperty("备注")
    private String note;

    @ExcelProperty("创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
