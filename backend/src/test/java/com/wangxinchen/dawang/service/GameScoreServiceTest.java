package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.LeaderboardEntry;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.repository.GameScoreRepository;
import com.wangxinchen.dawang.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GameScoreServiceTest {

    @Mock
    private GameScoreRepository gameScoreRepository;
    @Mock
    private UserRepository userRepository;

    private GameScoreService service;

    @BeforeEach
    void setUp() {
        service = new GameScoreService(gameScoreRepository, userRepository);
    }

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    @Test
    void leaderboard_masksOtherUsernamesAndFlagsSelfWithMe() {
        List<Object[]> raw = List.of(new Object[]{1L, 100}, new Object[]{2L, 80});
        when(gameScoreRepository.findLeaderboardRaw()).thenReturn(raw);
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(
                List.of(user(1L, "小王"), user(2L, "张三丰")));

        List<LeaderboardEntry> result = service.leaderboard(10, 1L);

        assertEquals(2, result.size());

        LeaderboardEntry me = result.stream().filter(LeaderboardEntry::getMe).findFirst().orElse(null);
        assertNotNull(me, "本人记录应标记 me=true");
        assertEquals("小王", me.getUsername(), "本人显示全名");
        assertEquals(100, me.getBestScore());

        LeaderboardEntry masked = result.stream().filter(e -> !e.getMe()).findFirst().orElse(null);
        assertNotNull(masked, "他人记录应 me=false");
        assertEquals("张*丰", masked.getUsername(), "他人用户名应被遮蔽（保留首字与尾字）");
    }

    @Test
    void leaderboard_masksTwoCharNameKeepingFirstAndLast() {
        List<Object[]> raw = List.<Object[]>of(new Object[]{5L, 50});
        when(gameScoreRepository.findLeaderboardRaw()).thenReturn(raw);
        when(userRepository.findAllById(List.of(5L))).thenReturn(List.of(user(5L, "小明")));

        List<LeaderboardEntry> result = service.leaderboard(10, 99L); // 当前用户非本人

        assertEquals("小*明", result.get(0).getUsername());
        assertFalse(result.get(0).getMe());
    }

    @Test
    void leaderboard_marksNobodyAsMeWhenAnonymous() {
        List<Object[]> raw = List.<Object[]>of(new Object[]{5L, 50});
        when(gameScoreRepository.findLeaderboardRaw()).thenReturn(raw);
        when(userRepository.findAllById(List.of(5L))).thenReturn(List.of(user(5L, "小明")));

        List<LeaderboardEntry> result = service.leaderboard(10, null); // 未登录

        assertFalse(result.get(0).getMe());
        assertEquals("小*明", result.get(0).getUsername());
    }
}
