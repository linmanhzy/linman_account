package com.wangxinchen.dawang.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户违规记录。按 userId + violationType 组合唯一，用于追踪重复违规。
 * 一周内超过阈值则通知管理员。
 */
@Data
@Entity
@Table(name = "user_violation", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "violation_type" })
})
public class UserViolation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "violation_type", nullable = false, length = 50)
    private String violationType;

    @Column(nullable = false)
    private int count;

    @Column(name = "first_at", nullable = false)
    private LocalDateTime firstAt;

    @Column(name = "last_at", nullable = false)
    private LocalDateTime lastAt;

    /** 是否已通知管理员（窗口期内只通知一次） */
    @Column(nullable = false)
    private boolean notified;
}
