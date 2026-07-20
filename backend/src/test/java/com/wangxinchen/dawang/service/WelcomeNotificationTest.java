package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.AuthResponse;
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

    private User baseUser(Long id, String username, LocalDateTime createdAt, boolean greeted) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash("enc");
        u.setRole(Role.USER);
        u.setStatus(UserStatus.ENABLED);
        u.setCreatedAt(createdAt);
        u.setFirstLoginGreetingSent(greeted);
        return u;
    }

    @Test
    void register_doesNotSendWelcomeNotification() {
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

        // 欢迎语改为「首次登录」时发送，注册时不应发
        verify(notificationService, never()).sendFirstLoginGreeting(any(), anyInt());
    }

    @Test
    void login_sendsFirstLoginGreetingWithRank() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        User u = baseUser(7L, "newuser", t0, false);

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass123", "enc")).thenReturn(true);
        when(userRepository.countByCreatedAtLessThanEqual(t0)).thenReturn(5L);
        when(userRepository.save(any(User.class))).thenAnswer(a -> a.getArgument(0));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("tok");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("newuser");
        loginReq.setPassword("pass123");
        authService.login(loginReq);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Integer> nthCap = ArgumentCaptor.forClass(Integer.class);
        verify(notificationService).sendFirstLoginGreeting(userCap.capture(), nthCap.capture());

        assertEquals(5, nthCap.getValue(), "应是第 5 位用户");
        assertTrue(userCap.getValue().getFirstLoginGreetingSent(), "发完后应标记已发送");
    }

    @Test
    void login_doesNotSendGreetingSecondTime() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 8, 0);
        User u = baseUser(7L, "newuser", t0, true); // 已发过

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass123", "enc")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(a -> a.getArgument(0));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("tok");

        LoginRequest loginReq2 = new LoginRequest();
        loginReq2.setUsername("newuser");
        loginReq2.setPassword("pass123");
        authService.login(loginReq2);

        verify(notificationService, never()).sendFirstLoginGreeting(any(), anyInt());
    }
}
