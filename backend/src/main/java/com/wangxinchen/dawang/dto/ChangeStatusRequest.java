package com.wangxinchen.dawang.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeStatusRequest {
    @NotBlank(message = "状态不能为空")
    private String status; // ENABLED / DISABLED
}
