package com.linman.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecordRequest {
    @NotBlank(message = "类型不能为空")
    private String type;                 // income / expense

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "日期不能为空")
    private LocalDate recordDate;

    private String categoryL1;

    private String categoryL2;

    private String note;
}
