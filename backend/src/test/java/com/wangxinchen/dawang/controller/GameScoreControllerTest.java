package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.dto.GameScoreResponse;
import com.wangxinchen.dawang.dto.GameScoreSummary;
import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserStatus;
import com.wangxinchen.dawang.security.CustomUserDetails;
import com.wangxinchen.dawang.service.GameScoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GameScoreControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private GameScoreService service;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setId(1L);
        u.setUsername("tester");
        u.setPasswordHash("x");
        u.setRole(Role.USER);
        u.setStatus(UserStatus.ENABLED);
        CustomUserDetails principal = CustomUserDetails.from(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submit_returnsOk() throws Exception {
        GameScoreResponse resp = new GameScoreResponse();
        resp.setId(1L);
        resp.setScore(10);
        when(service.save(anyLong(), any())).thenReturn(resp);
        mvc.perform(post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":10}"))
                .andExpect(status().isOk());
    }

    @Test
    void submit_negativeScore_isRejected() throws Exception {
        // 本项目统一返回体：校验失败返回 HTTP 200 + body.code=400（由全局异常处理包装）
        mvc.perform(post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void my_returnsOk() throws Exception {
        when(service.my(anyLong())).thenReturn(new GameScoreSummary());
        mvc.perform(get("/api/game/my")).andExpect(status().isOk());
    }

    @Test
    void leaderboard_returnsOk() throws Exception {
        when(service.leaderboard(anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/game/leaderboard")).andExpect(status().isOk());
    }
}
