package com.wangxinchen.dawang.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "scheduled_notification")
public class ScheduledNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency = Frequency.DAILY;  // 频率：DAILY / SPECIFIC_DATE

    @Column(name = "send_time")
    private LocalTime sendTime;   // 发送时间（HH:mm），如 09:00

    @Column(name = "send_date")
    private LocalDate sendDate;   // 指定日期（仅 SPECIFIC_DATE 时使用）

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type = NotificationType.DAILY;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "target_user_id")
    private Long targetUserId;  // null 表示全体用户，非 null 表示指定用户

    @Column(name = "last_fire_date")
    private LocalDate lastFireDate; // 上次触发日期，用于防止同日重复

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
