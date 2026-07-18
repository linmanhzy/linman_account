package com.linman.account.controller;

import com.linman.account.common.Result;
import com.linman.account.dto.GameScoreRequest;
import com.linman.account.dto.GameScoreResponse;
import com.linman.account.dto.GameScoreSummary;
import com.linman.account.dto.LeaderboardEntry;
import com.linman.account.security.SecurityHelper;
import com.linman.account.service.GameScoreService;
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

    @Operation(summary = "查看全局排行榜（按最高分降序）")
    @GetMapping("/leaderboard")
    public Result<List<LeaderboardEntry>> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(gameScoreService.leaderboard(limit));
    }
}
