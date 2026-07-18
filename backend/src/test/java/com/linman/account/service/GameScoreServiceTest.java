package com.linman.account.service;

import com.linman.account.dto.GameScoreRequest;
import com.linman.account.dto.GameScoreSummary;
import com.linman.account.dto.LeaderboardEntry;
import com.linman.account.entity.User;
import com.linman.account.repository.GameScoreRepository;
import com.linman.account.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(GameScoreService.class)
class GameScoreServiceTest {

    @Autowired
    private GameScoreService service;
    @Autowired
    private GameScoreRepository gameScoreRepository;
    @Autowired
    private UserRepository userRepository;

    private User newUser(String name) {
        User u = new User();
        u.setUsername(name);
        u.setPasswordHash("pwd");
        return userRepository.save(u);
    }

    private GameScoreRequest req(int s) {
        GameScoreRequest r = new GameScoreRequest();
        r.setScore(s);
        return r;
    }

    @Test
    void save_trimsToKeepAtMostSixRowsPerUser() {
        User u = newUser("alice");
        for (int i = 0; i < 10; i++) {
            service.save(u.getId(), req(i));
        }
        assertThat(gameScoreRepository.countByUserId(u.getId())).isLessThanOrEqualTo(6);
        // 最高分（最后一次 9）应被保留
        assertThat(service.my(u.getId()).getBestScore()).isEqualTo(9);
    }

    @Test
    void my_defaultsBestToZeroWhenNoScores() {
        User u = newUser("bob");
        GameScoreSummary s = service.my(u.getId());
        assertThat(s.getBestScore()).isEqualTo(0);
        assertThat(s.getRecent()).isEmpty();
    }

    @Test
    void leaderboard_usesUsernameAndSortsByBestDesc() {
        User u1 = newUser("alice");
        User u2 = newUser("bob");
        service.save(u1.getId(), req(25));
        service.save(u1.getId(), req(5));
        service.save(u2.getId(), req(40));

        List<LeaderboardEntry> lb = service.leaderboard(20);

        assertThat(lb).hasSize(2);
        assertThat(lb.get(0).getUsername()).isEqualTo("bob");
        assertThat(lb.get(0).getBestScore()).isEqualTo(40);
        assertThat(lb.get(1).getUsername()).isEqualTo("alice");
        assertThat(lb.get(1).getBestScore()).isEqualTo(25);
    }
}
