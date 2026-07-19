package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.GameScoreRequest;
import com.wangxinchen.dawang.dto.GameScoreResponse;
import com.wangxinchen.dawang.dto.GameScoreSummary;
import com.wangxinchen.dawang.dto.LeaderboardEntry;
import com.wangxinchen.dawang.entity.GameScore;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.repository.GameScoreRepository;
import com.wangxinchen.dawang.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GameScoreService {
    private final GameScoreRepository gameScoreRepository;
    private final UserRepository userRepository;

    public GameScoreService(GameScoreRepository gameScoreRepository, UserRepository userRepository) {
        this.gameScoreRepository = gameScoreRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GameScoreResponse save(Long userId, GameScoreRequest req) {
        GameScore g = new GameScore();
        g.setUserId(userId);
        g.setScore(req.getScore());
        g.setPlayedAt(LocalDateTime.now());
        g = gameScoreRepository.save(g);
        // 每人只保留「历史最高那条 + 最近 5 次」，其余裁剪
        trimToKeep(userId);
        return toResponse(g);
    }

    public GameScoreSummary my(Long userId) {
        Integer bestScore = gameScoreRepository.findTopByUserIdOrderByScoreDesc(userId)
                .map(GameScore::getScore)
                .orElse(0);
        List<GameScoreResponse> recent = gameScoreRepository
                .findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, 5))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        GameScoreSummary s = new GameScoreSummary();
        s.setBestScore(bestScore);
        s.setRecent(recent);
        return s;
    }

    public List<LeaderboardEntry> leaderboard(int limit) {
        if (limit <= 0) limit = 20;
        // 防止前端传入超大值导致全量加载/内存压力
        limit = Math.min(limit, 100);
        List<Object[]> rows = gameScoreRepository.findLeaderboardRaw();
        int end = Math.min(limit, rows.size());
        List<Long> userIds = rows.subList(0, end).stream()
                .map(r -> (Long) r[0])
                .collect(Collectors.toList());
        Map<Long, String> nameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        List<LeaderboardEntry> result = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            Object[] row = rows.get(i);
            LeaderboardEntry e = new LeaderboardEntry();
            e.setUserId((Long) row[0]);
            e.setBestScore((Integer) row[1]);
            e.setUsername(nameMap.getOrDefault((Long) row[0], "未知用户"));
            result.add(e);
        }
        return result;
    }

    @Transactional
    public void trimToKeep(Long userId) {
        List<GameScore> recent = gameScoreRepository.findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, 5));
        GameScore best = gameScoreRepository.findTopByUserIdOrderByScoreDesc(userId).orElse(null);
        Set<Long> keepIds = recent.stream().map(GameScore::getId).collect(Collectors.toSet());
        if (best != null) {
            keepIds.add(best.getId());
        }
        List<Long> toDelete = gameScoreRepository
                .findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .map(GameScore::getId)
                .filter(id -> !keepIds.contains(id))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            gameScoreRepository.deleteAllById(toDelete);
        }
    }

    private GameScoreResponse toResponse(GameScore g) {
        GameScoreResponse d = new GameScoreResponse();
        d.setId(g.getId());
        d.setScore(g.getScore());
        d.setPlayedAt(g.getPlayedAt());
        return d;
    }
}
