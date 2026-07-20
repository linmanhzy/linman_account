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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.notificationService = notificationService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BizException(409, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ENABLED);
        user.setCreatedAt(LocalDateTime.now());
        user.setLastLoginAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        // 注册完成即向新用户本人发送欢迎语（第 N 位用户），收件人毫无歧义
        int rank = (int) userRepository.countByCreatedAtLessThanEqual(saved.getCreatedAt());
        notificationService.sendRegistrationWelcome(saved, rank);
        return buildAuth(saved);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException(403, "该账户已被禁用，请联系管理员");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return buildAuth(user);
    }

    private AuthResponse buildAuth(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        AuthResponse resp = new AuthResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole().name());
        resp.setUserId(user.getId());
        return resp;
    }
}
