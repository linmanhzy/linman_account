package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.dto.NotificationRequest;
import com.wangxinchen.dawang.dto.NotificationResponse;
import com.wangxinchen.dawang.dto.UnreadCountResponse;
import com.wangxinchen.dawang.entity.Notification;
import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.repository.NotificationRepository;
import com.wangxinchen.dawang.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    NotificationService notificationService;

    @Test
    void unreadCount_shouldReturnCorrectCount() {
        when(notificationRepository.countByUserIdAndIsRead(1L, false)).thenReturn(5L);
        UnreadCountResponse r = notificationService.unreadCount(1L);
        assertEquals(5L, r.getCount());
    }

    @Test
    void sendToAll_shouldCreateForEachUser() {
        User u1 = new User();
        u1.setId(2L);
        u1.setRole(Role.USER);
        User u2 = new User();
        u2.setId(3L);
        u2.setRole(Role.USER);

        when(userRepository.findByRole(Role.USER)).thenReturn(List.of(u1, u2));

        NotificationRequest req = new NotificationRequest();
        req.setTitle("公告");
        req.setContent("全体通知内容");

        notificationService.sendToAll(req);
        verify(notificationRepository, times(1)).saveAll(any());
    }

    @Test
    void sendToUser_shouldCreateSingleNotification() {
        User target = new User();
        target.setId(5L);

        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        NotificationRequest req = new NotificationRequest();
        req.setTitle("私信");
        req.setContent("针对你的回复");

        notificationService.sendToUser(5L, req);
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void myNotifications_shouldReturnUserNotifications() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(1L);
        n.setTitle("标题");
        n.setContent("内容");
        n.setIsRead(false);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n));

        List<NotificationResponse> list = notificationService.myNotifications(1L);
        assertEquals(1, list.size());
        assertEquals("标题", list.get(0).getTitle());
        assertFalse(list.get(0).getIsRead());
    }
}
