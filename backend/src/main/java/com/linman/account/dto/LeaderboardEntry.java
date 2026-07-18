package com.linman.account.dto;

import lombok.Data;

@Data
public class LeaderboardEntry {
    private Long userId;
    private String username;
    private Integer bestScore;
}
