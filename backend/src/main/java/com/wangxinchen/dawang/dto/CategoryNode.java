package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.util.List;

@Data
public class CategoryNode {
    private Long id;
    private String name;
    private String type;
    private String icon;
    private boolean system;
    private List<CategoryNode> children;
}
