package com.linman.account.repository;

import com.linman.account.entity.GameScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class GameScoreRepositoryTest {

    @Autowired
    private GameScoreRepository gameScoreRepository;

    private GameScore score(Long userId, int s, int minutesAgo) {
        GameScore g = new GameScore();
        g.setUserId(userId);
        g.setScore(s);
        g.setPlayedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        return gameScoreRepository.save(g);
    }

    @Test
    void findTopByUserIdOrderByScoreDesc_returnsHighest() {
        score(1L, 10, 30);
        score(1L, 50, 20);
        score(1L, 30, 10);

        Optional<GameScore> best = gameScoreRepository.findTopByUserIdOrderByScoreDesc(1L);

        assertThat(best).isPresent();
        assertThat(best.get().getScore()).isEqualTo(50);
    }

    @Test
    void findByUserIdOrderByPlayedAtDesc_limitsRecent() {
        for (int i = 1; i <= 8; i++) {
            score(2L, i, 100 - i);
        }

        List<GameScore> recent = gameScoreRepository.findByUserIdOrderByPlayedAtDesc(2L, PageRequest.of(0, 5));

        assertThat(recent).hasSize(5);
        assertThat(recent.get(0).getPlayedAt()).isAfterOrEqualTo(recent.get(4).getPlayedAt());
    }

    @Test
    void findLeaderboardRaw_groupsByUserAndMaxDesc() {
        score(3L, 5, 30);
        score(3L, 25, 10);
        score(4L, 40, 5);

        List<Object[]> rows = gameScoreRepository.findLeaderboardRaw();

        assertThat(rows).hasSize(2);
        assertThat((Long) rows.get(0)[0]).isEqualTo(4L);
        assertThat((Integer) rows.get(0)[1]).isEqualTo(40);
        assertThat((Long) rows.get(1)[0]).isEqualTo(3L);
        assertThat((Integer) rows.get(1)[1]).isEqualTo(25);
    }
}
