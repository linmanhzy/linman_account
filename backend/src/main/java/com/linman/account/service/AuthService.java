package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.AuthResponse;
import com.linman.account.dto.LoginRequest;
import com.linman.account.dto.RegisterRequest;
import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import com.linman.account.repository.UserRepository;
import com.linman.account.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
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
        User saved = userRepository.save(user);
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
