package com.wangxinchen.dawang.dto;

import lombok.Data;

import java.util.List;

@Data
public class GameScoreSummary {
    private Integer bestScore;
    private List<GameScoreResponse> recent;
}
