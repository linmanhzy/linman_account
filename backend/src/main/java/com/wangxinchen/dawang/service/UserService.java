package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.common.BizException;
import com.wangxinchen.dawang.dto.*;
import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserStatus;
import com.wangxinchen.dawang.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${myapp.admin.username:admin}")
    private String defaultAdminUsername;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummaryDto> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<UserSimpleDto> listSimpleUsers() {
        return userRepository.findAll().stream()
                .map(u -> {
                    UserSimpleDto d = new UserSimpleDto();
                    d.setId(u.getId());
                    d.setUsername(u.getUsername());
                    return d;
                }).collect(Collectors.toList());
    }

    @Transactional
    public UserSummaryDto changeStatus(Long id, String statusStr, Long operatorId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        UserStatus status;
        try {
            status = UserStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (Exception e) {
            throw new BizException(400, "非法的状态值，应为 ENABLED 或 DISABLED");
        }
        // 不允许管理员禁用自己
        if (user.getId().equals(operatorId) && status == UserStatus.DISABLED) {
            throw new BizException(400, "不能禁用自己的账户");
        }
        // 不允许禁用默认管理员，确保系统始终保留一个可登录的管理员
        if (user.getUsername().equals(defaultAdminUsername) && status == UserStatus.DISABLED) {
            throw new BizException(400, "默认管理员账号不能被禁用");
        }
        // 不允许禁用最后一个启用的管理员
        if (user.getRole() == Role.ADMIN && status == UserStatus.DISABLED) {
            long adminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ENABLED);
            if (adminCount <= 1) {
                throw new BizException(400, "不能禁用系统中最后一个启用的管理员");
            }
        }
        user.setStatus(status);
        return toDto(userRepository.save(user));
    }

    public UserSummaryDto createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BizException(409, "用户名已存在");
        }
        Role role;
        try {
            role = Role.valueOf(req.getRole().trim().toUpperCase());
        } catch (Exception e) {
            throw new BizException(400, "非法的角色值，应为 USER 或 ADMIN");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole(role);
        user.setStatus(UserStatus.ENABLED);
        user.setCreatedAt(LocalDateTime.now());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserSummaryDto updateUser(Long id, UpdateUserRequest req, Long operatorId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            // 检查用户名是否已被其他用户占用
            userRepository.findByUsername(req.getUsername()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BizException(409, "用户名已被占用");
                }
            });
            user.setUsername(req.getUsername().trim());
        }
        if (req.getRole() != null && !req.getRole().isBlank()) {
            Role role;
            try {
                role = Role.valueOf(req.getRole().trim().toUpperCase());
            } catch (Exception e) {
                throw new BizException(400, "非法的角色值，应为 USER 或 ADMIN");
            }
            // 不允许将当前登录的管理员降级为普通用户
            if (user.getId().equals(operatorId) && role == Role.USER) {
                throw new BizException(400, "不能修改自己的角色");
            }
            // 不允许剥夺最后一个管理员的角色
            if (user.getRole() == Role.ADMIN && role == Role.USER) {
                long adminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ENABLED);
                if (adminCount <= 1) {
                    throw new BizException(400, "不能移除系统中最后一个管理员的角色");
                }
            }
            user.setRole(role);
        }
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id, Long operatorId, String operatorName) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (user.getId().equals(operatorId)) {
            throw new BizException(400, "不能删除自己的账户");
        }
        // 禁止删除最后一个处于启用状态的管理员（与 changeStatus 口径对齐）
        if (user.getRole() == Role.ADMIN) {
            long enabledAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ENABLED);
            if (enabledAdminCount <= 1) {
                throw new BizException(400, "不能删除系统中最后一个管理员账户");
            }
        }
        userRepository.delete(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    private UserSummaryDto toDto(User user) {
        UserSummaryDto dto = new UserSummaryDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole().name());
        dto.setStatus(user.getStatus().name());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
