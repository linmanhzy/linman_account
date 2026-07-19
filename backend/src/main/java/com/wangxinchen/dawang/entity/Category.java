package com.wangxinchen.dawang.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String type;                 // income / expense

    @Column(length = 10)
    private String icon;                 // 仅大类(L1)携带图标

    @Column(name = "parent_id")
    private Long parentId;               // null = 大类(L1)

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;      // 系统预设分类（只读）

    @Column(name = "user_id")
    private Long userId;                 // null = 系统预设；否则为创建者

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
