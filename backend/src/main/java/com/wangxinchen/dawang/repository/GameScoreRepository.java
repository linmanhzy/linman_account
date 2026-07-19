package com.wangxinchen.dawang.repository;

import com.wangxinchen.dawang.entity.GameScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {

    Optional<GameScore> findTopByUserIdOrderByScoreDesc(Long userId);

    List<GameScore> findByUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT g.userId, MAX(g.score) FROM GameScore g GROUP BY g.userId ORDER BY MAX(g.score) DESC")
    List<Object[]> findLeaderboardRaw();

    long countByUserId(Long userId);
}
