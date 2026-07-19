package com.wangxinchen.dawang.dto;

import com.wangxinchen.dawang.entity.Frequency;
import com.wangxinchen.dawang.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ScheduledNotificationRequest {
    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @NotNull(message = "频率不能为空")
    private Frequency frequency;

    @NotNull(message = "发送时间不能为空")
    private LocalTime sendTime;

    // SPECIFIC_DATE 时需要的指定日期
    private LocalDate sendDate;

    private NotificationType type = NotificationType.DAILY;

    // 为 null 表示发给所有用户
    private Long targetUserId;
}
