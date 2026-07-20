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

    public List<LeaderboardEntry> leaderboard(int limit, Long currentUserId) {
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
            Long uid = (Long) row[0];
            boolean self = currentUserId != null && uid.equals(currentUserId);
            String rawName = nameMap.getOrDefault(uid, "未知用户");
            LeaderboardEntry e = new LeaderboardEntry();
            e.setUserId(uid);
            e.setBestScore((Integer) row[1]);
            e.setMe(self);
            // 保护隐私：非本人用户名遮蔽（保留首字以及尾字），本人显示全名
            e.setUsername(self ? rawName : maskUsername(rawName));
            result.add(e);
        }
        return result;
    }

    /**
     * 用户名脱敏：保留首字与尾字，中间用 * 遮蔽（至少 1 个），「未知用户」不处理。
     * 例：张三丰→张*丰，王小明明→王**明。
     */
    private String maskUsername(String name) {
        if (name == null || name.length() <= 1 || "未知用户".equals(name)) {
            return name;
        }
        int stars = Math.max(name.length() - 2, 1);
        return name.substring(0, 1) + "*".repeat(stars) + name.substring(name.length() - 1);
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
