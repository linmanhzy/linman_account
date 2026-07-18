package com.linman.account.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String type;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "is_system")
    private Boolean isSystem = false;

    @Column(name = "user_id")
    private Long userId;                   // 为 NULL 表示系统预设分类
}
