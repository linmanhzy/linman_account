package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GameScoreResponse {
    private Long id;
    private Integer score;
    private LocalDateTime playedAt;
}
