package com.wangxinchen.dawang.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Size(min = 2, max = 32, message = "用户名长度为2-32个字符")
    private String username;

    private String role; // USER 或 ADMIN，可选
}
