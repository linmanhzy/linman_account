package com.linman.account.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSummaryDto {
    private Long id;
    private String username;
    private String role;
    private String status;
    private LocalDateTime createdAt;
}
