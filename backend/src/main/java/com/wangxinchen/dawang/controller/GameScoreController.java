package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.GameScoreRequest;
import com.wangxinchen.dawang.dto.GameScoreResponse;
import com.wangxinchen.dawang.dto.GameScoreSummary;
import com.wangxinchen.dawang.dto.LeaderboardEntry;
import com.wangxinchen.dawang.security.SecurityHelper;
import com.wangxinchen.dawang.service.GameScoreService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/game")
public class GameScoreController {
    private final GameScoreService gameScoreService;

    public GameScoreController(GameScoreService gameScoreService) {
        this.gameScoreService = gameScoreService;
    }

    @Operation(summary = "保存一局贪吃蛇成绩")
    @PostMapping("/scores")
    public Result<GameScoreResponse> submit(@Valid @RequestBody GameScoreRequest req) {
        return Result.ok(gameScoreService.save(SecurityHelper.getCurrentUserId(), req));
    }

    @Operation(summary = "查看我的游戏成绩汇总（历史最高 + 最近5次）")
    @GetMapping("/my")
    public Result<GameScoreSummary> my() {
        return Result.ok(gameScoreService.my(SecurityHelper.getCurrentUserId()));
    }

    @Operation(summary = "查看全局排行榜（按最高分降序，非本人用户名脱敏）")
    @GetMapping("/leaderboard")
    public Result<List<LeaderboardEntry>> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(gameScoreService.leaderboard(limit, SecurityHelper.getCurrentUserId()));
    }
}
