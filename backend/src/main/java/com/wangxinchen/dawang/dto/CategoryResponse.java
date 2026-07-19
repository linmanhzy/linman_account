package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryResponse {
    private Long id;
    private String name;
    private String type;
    private String icon;
    private Long parentId;
    private int sortOrder;
    private boolean system;
    private Long userId;
    private LocalDateTime createdAt;
}
