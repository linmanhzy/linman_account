package com.wangxinchen.dawang.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationRequest {
    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    // 为空/null 表示发给全体用户，否则发给指定用户
    private Long targetUserId;
}
