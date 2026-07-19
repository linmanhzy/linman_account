package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.RegisterRequest;
import com.wangxinchen.dawang.entity.NotificationType;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.repository.UserRepository;
import com.wangxinchen.dawang.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldCreateWelcomeNotification() {
        // given
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("password123");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("mock_token");

        // when
        authService.register(req);

        // then - 验证 welcome 通知被创建
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(notificationService, times(1)).createWelcomeNotification(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("newuser", capturedUser.getUsername());
        assertEquals(42L, capturedUser.getId());
    }

    @Test
    void secondRegister_shouldNotDuplicateWelcomeNotification() {
        // given
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("password123");

        when(userRepository.existsByUsername("newuser")).thenReturn(true);

        // when - 注册应失败（用户名已存在）
        try {
            authService.register(req);
            fail("应抛出异常");
        } catch (Exception e) {
            // expected
        }

        // then - 不应创建任何通知
        verify(notificationService, never()).createWelcomeNotification(any());
    }
}
