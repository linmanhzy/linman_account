package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.UserSummaryDto;
import com.linman.account.entity.User;
import com.linman.account.entity.UserStatus;
import com.linman.account.repository.UserRepository;
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

    public UserSummaryDto changeStatus(Long id, String statusStr) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        UserStatus status;
        try {
            status = UserStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (Exception e) {
            throw new BizException(400, "非法的状态值，应为 ENABLED 或 DISABLED");
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
