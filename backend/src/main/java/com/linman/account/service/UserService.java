package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.UserSimpleDto;
import com.linman.account.dto.UserSummaryDto;
import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import com.linman.account.repository.UserRepository;
import com.linman.account.security.SecurityHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    public UserSummaryDto changeStatus(Long id, String statusStr) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        UserStatus status;
        try {
            status = UserStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (Exception e) {
            throw new BizException(400, "非法的状态值，应为 ENABLED 或 DISABLED");
        }
        // 不允许管理员禁用自己
        Long currentUserId = SecurityHelper.getCurrentUserId();
        if (user.getId().equals(currentUserId) && status == UserStatus.DISABLED) {
            throw new BizException(400, "不能禁用自己的账户");
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
