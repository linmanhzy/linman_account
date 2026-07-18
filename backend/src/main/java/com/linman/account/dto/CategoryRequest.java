package com.linman.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "名称不能为空")
    private String name;

    private String type;                 // 仅大类(L1)需要：income / expense

    private String icon;                 // 仅大类(L1)需要图标

    private Long parentId;              // 为空表示大类(L1)，否则为所属大类 id
}
