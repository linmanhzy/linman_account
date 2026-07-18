package com.linman.account.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeedbackResponse {
    private Long id;
    private String content;
    private String status;
    private String reply;
    private LocalDateTime createdAt;
    private Long userId;
    private String username;
}
