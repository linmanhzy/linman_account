package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.LoginRequest;
import com.wangxinchen.dawang.dto.RegisterRequest;
import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserStatus;
import com.wangxinchen.dawang.repository.UserRepository;
import com.wangxinchen.dawang.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WelcomeNotificationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private NotificationService notificationService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, notificationService);
    }

    private User baseUser(Long id, String username, LocalDateTime createdAt) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash("enc");
        u.setRole(Role.USER);
        u.setStatus(UserStatus.ENABLED);
        u.setCreatedAt(createdAt);
        return u;
    }

    // 注册完成即向新用户本人发送欢迎语（第 N 位）
    @Test
    void register_sendsWelcomeNotificationToNewUser() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("pass123");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenAnswer(a -> {
            User u = a.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("tok");

        authService.register(req);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Integer> rankCap = ArgumentCaptor.forClass(Integer.class);
        verify(notificationService).sendRegistrationWelcome(userCap.capture(), rankCap.capture());

        assertEquals(1L, userCap.getValue().getId(), "欢迎语必须发给刚注册的新用户本人");
    }

    // 欢迎语中的「第 N 位」由 countByCreatedAtLessThanEqual 决定
    @Test
    void register_computesRankByCreatedAt() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("rankuser");
        req.setPassword("pass123");

        when(userRepository.existsByUsername("rankuser")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenAnswer(a -> {
            User u = a.getArgument(0);
            u.setId(9L);
            u.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
            return u;
        });
        when(userRepository.countByCreatedAtLessThanEqual(any(LocalDateTime.class))).thenReturn(7L);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("tok");

        authService.register(req);

        ArgumentCaptor<Integer> rankCap = ArgumentCaptor.forClass(Integer.class);
        verify(notificationService).sendRegistrationWelcome(any(User.class), rankCap.capture());
        assertEquals(7, rankCap.getValue(), "应为第 7 位用户");
    }

    // 登录不再发送欢迎语（已改到注册完成时触发）
    @Test
    void login_doesNotSendWelcomeNotification() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        User u = baseUser(7L, "newuser", t0);

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass123", "enc")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(a -> a.getArgument(0));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("tok");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("newuser");
        loginReq.setPassword("pass123");
        authService.login(loginReq);

        verify(notificationService, never()).sendRegistrationWelcome(any(), anyInt());
    }
}
