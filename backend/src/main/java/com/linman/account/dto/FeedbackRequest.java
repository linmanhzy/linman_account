package com.linman.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotBlank(message = "反馈内容不能为空")
    private String content;
}
