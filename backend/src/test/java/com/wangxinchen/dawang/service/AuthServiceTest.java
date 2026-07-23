package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.common.BizException;
import com.wangxinchen.dawang.dto.AuthResponse;
import com.wangxinchen.dawang.dto.LoginRequest;
import com.wangxinchen.dawang.dto.RegisterRequest;
import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserStatus;
import com.wangxinchen.dawang.repository.UserRepository;
import com.wangxinchen.dawang.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试（Mockito 风格，纯单元）
 *
 * 覆盖：register（成功/重名）、login（成功/用户名错/密码错/账号禁用）。
 * 不依赖 Spring 容器，秒级完成。
 *
 * 关键不变量：
 * - 注册成功必须发欢迎通知（sendRegistrationWelcome），并传"第 N 位"序号
 * - 登录成功必须更新 lastLoginAt
 * - 密码错 / 用户名错 给出相同的 401 信息（防用户名枚举）
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock NotificationService notificationService;

    @InjectMocks AuthService authService;

    private RegisterRequest registerReq(String u, String p) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(u);
        r.setPassword(p);
        return r;
    }

    private LoginRequest loginReq(String u, String p) {
        LoginRequest r = new LoginRequest();
        r.setUsername(u);
        r.setPassword(p);
        return r;
    }

    private User existingUser(String username, String rawPwd, Role role, UserStatus status) {
        User u = new User();
        u.setId(100L);
        u.setUsername(username);
        // passwordHash 占位，调用方会用 mock 决定 matches 结果
        u.setPasswordHash("__hash__" + rawPwd);
        u.setRole(role);
        u.setStatus(status);
        u.setCreatedAt(LocalDateTime.now().minusDays(1));
        return u;
    }

    // ============ register ============

    @Test
    void register_newUsername_shouldReturnTokenAndTriggerWelcomeNotification() {
        RegisterRequest req = registerReq("newuser", "secret123");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            u.setCreatedAt(LocalDateTime.now());
            return u;
        });
        when(userRepository.countByCreatedAtLessThanEqual(any())).thenReturn(7L);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("JWT-FAKE");

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("JWT-FAKE", resp.getToken());
        assertEquals("newuser", resp.getUsername());
        assertEquals("USER", resp.getRole());
        assertEquals(1L, resp.getUserId());

        // 必须把密码用 encoder 编码（不能存明文）
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("ENCODED", saved.getValue().getPasswordHash(), "密码必须编码后落库，不能明文");
        assertEquals(Role.USER, saved.getValue().getRole(), "默认角色应为 USER");

        // 必须触发欢迎通知，rank 来自 countByCreatedAtLessThanEqual
        verify(notificationService, times(1)).sendRegistrationWelcome(any(User.class), eq(7));
    }

    @Test
    void register_duplicateUsername_shouldThrow409() {
        when(userRepository.existsByUsername("dup")).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> authService.register(registerReq("dup", "x12345")));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("用户名已存在"));

        // 不应再调 save / 发通知
        verify(userRepository, never()).save(any());
        verify(notificationService, never()).sendRegistrationWelcome(any(), anyInt());
    }

    // ============ login ============

    @Test
    void login_correctCredentials_shouldReturnTokenAndUpdateLastLogin() {
        User u = existingUser("alice", "secret123", Role.USER, UserStatus.ENABLED);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret123", u.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(u.getId(), "alice", "USER")).thenReturn("JWT-OK");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse resp = authService.login(loginReq("alice", "secret123"));

        assertEquals("JWT-OK", resp.getToken());
        assertEquals("alice", resp.getUsername());
        assertEquals("USER", resp.getRole());
        assertEquals(u.getId(), resp.getUserId());

        // lastLoginAt 必须被更新并落库
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertNotNull(saved.getValue().getLastLoginAt());
    }

    @Test
    void login_usernameNotFound_shouldThrow401() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(loginReq("ghost", "any")));
        assertEquals(401, ex.getCode());

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
    }

    @Test
    void login_wrongPassword_shouldThrow401AndNotLeakUsernameExistence() {
        User u = existingUser("bob", "right", Role.USER, UserStatus.ENABLED);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", u.getPasswordHash())).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(loginReq("bob", "wrong")));
        assertEquals(401, ex.getCode());
        // 关键：用户名错 / 密码错的提示文案必须一致 → 防用户名枚举
        assertEquals("用户名或密码错误", ex.getMessage());

        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_disabledAccount_shouldThrow403() {
        User u = existingUser("eve", "pwd123", Role.USER, UserStatus.DISABLED);
        when(userRepository.findByUsername("eve")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pwd123", u.getPasswordHash())).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> authService.login(loginReq("eve", "pwd123")));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("禁用"));

        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
    }

}
