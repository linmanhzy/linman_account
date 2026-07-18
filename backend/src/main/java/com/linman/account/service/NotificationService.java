package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.NotificationRequest;
import com.linman.account.dto.NotificationResponse;
import com.linman.account.dto.UnreadCountResponse;
import com.linman.account.entity.Notification;
import com.linman.account.entity.Role;
import com.linman.account.entity.User;
import com.linman.account.repository.NotificationRepository;
import com.linman.account.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /* ===== 普通用户 ===== */

    public List<NotificationResponse> myNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public UnreadCountResponse unreadCount(Long userId) {
        return UnreadCountResponse.of(notificationRepository.countByUserIdAndIsRead(userId, false));
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BizException(404, "通知不存在"));
        if (!n.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该通知");
        }
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /* ===== 管理员发送通知 ===== */

    @Transactional
    public void sendToAll(NotificationRequest req) {
        // 无普通用户时静默成功（影响 0 人），不再抛 400，避免「系统尚未有注册用户」时管理员无法发布公告
        List<User> users = userRepository.findByRole(Role.USER);
        if (users.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = users.stream().map(u -> {
            Notification n = new Notification();
            n.setUserId(u.getId());
            n.setTitle(req.getTitle());
            n.setContent(req.getContent());
            n.setIsRead(false);
            n.setCreatedAt(now);
            return n;
        }).collect(Collectors.toList());
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void sendToUser(Long targetUserId, NotificationRequest req) {
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new BizException(404, "目标用户不存在"));
        Notification n = new Notification();
        n.setUserId(targetUserId);
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    private NotificationResponse toDto(Notification n) {
        NotificationResponse d = new NotificationResponse();
        d.setId(n.getId());
        d.setTitle(n.getTitle());
        d.setContent(n.getContent());
        d.setIsRead(n.getIsRead());
        d.setCreatedAt(n.getCreatedAt());
        return d;
    }
}
