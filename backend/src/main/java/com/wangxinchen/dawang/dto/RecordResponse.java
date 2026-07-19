package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RecordResponse {
    private Long id;
    private String type;
    private BigDecimal amount;
    private LocalDate recordDate;
    private String categoryL1;
    private String categoryL2;
    private String note;
    private LocalDateTime createdAt;
}
