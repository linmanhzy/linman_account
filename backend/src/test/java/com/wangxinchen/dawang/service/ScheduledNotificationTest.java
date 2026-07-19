package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.entity.Frequency;
import com.wangxinchen.dawang.entity.NotificationType;
import com.wangxinchen.dawang.entity.ScheduledNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.wangxinchen.dawang.repository.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ScheduledNotificationTest {

    @Mock
    private ScheduledNotificationRepository scheduledNotificationRepository;
    @Mock
    private NotificationService notificationService;

    private ScheduledNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ScheduledNotificationDispatcher(
                scheduledNotificationRepository, notificationService);
    }

    // ===== DAILY 频率测试 =====

    @Test
    void daily_shouldSendWhenTimePassed() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 5);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(1L);
        sn.setTitle("每日记账提醒");
        sn.setContent("记得今天的账哦！");
        sn.setFrequency(Frequency.DAILY);
        sn.setSendTime(LocalTime.of(9, 0));
        sn.setType(NotificationType.DAILY);
        sn.setEnabled(true);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, times(1)).sendScheduledToAll(sn);
        verify(scheduledNotificationRepository, times(1)).save(sn);
        assertEquals(today, sn.getLastFireDate());
    }

    @Test
    void daily_shouldNotSendBeforeTime() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 8, 0);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(1L);
        sn.setTitle("每日记账提醒");
        sn.setContent("记得今天的账哦！");
        sn.setFrequency(Frequency.DAILY);
        sn.setSendTime(LocalTime.of(9, 0));
        sn.setType(NotificationType.DAILY);
        sn.setEnabled(true);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void daily_shouldNotSendTwiceOnSameDay() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 5);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(1L);
        sn.setTitle("每日记账提醒");
        sn.setContent("记得今天的账哦！");
        sn.setFrequency(Frequency.DAILY);
        sn.setSendTime(LocalTime.of(9, 0));
        sn.setType(NotificationType.DAILY);
        sn.setEnabled(true);
        sn.setLastFireDate(today); // 今日已发

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    // ===== SPECIFIC_DATE 频率测试 =====

    @Test
    void specificDate_shouldSendOnMatchDay() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 10, 5);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(2L);
        sn.setTitle("国庆祝福");
        sn.setContent("国庆节快乐！");
        sn.setFrequency(Frequency.SPECIFIC_DATE);
        sn.setSendDate(LocalDate.of(2026, 10, 1));
        sn.setSendTime(LocalTime.of(10, 0));
        sn.setType(NotificationType.HOLIDAY);
        sn.setEnabled(true);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, times(1)).sendScheduledToAll(sn);
        verify(scheduledNotificationRepository, times(1)).save(sn);
        assertEquals(today, sn.getLastFireDate());
    }

    @Test
    void specificDate_shouldNotSendOnNonMatchDay() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 10, 5);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(2L);
        sn.setTitle("国庆祝福");
        sn.setContent("国庆节快乐！");
        sn.setFrequency(Frequency.SPECIFIC_DATE);
        sn.setSendDate(LocalDate.of(2026, 10, 1));
        sn.setSendTime(LocalTime.of(10, 0));
        sn.setType(NotificationType.HOLIDAY);
        sn.setEnabled(true);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void specificDate_shouldNotSendBeforeTimeOnMatchDay() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 9, 0);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(2L);
        sn.setTitle("国庆祝福");
        sn.setContent("国庆节快乐！");
        sn.setFrequency(Frequency.SPECIFIC_DATE);
        sn.setSendDate(LocalDate.of(2026, 10, 1));
        sn.setSendTime(LocalTime.of(10, 0));
        sn.setType(NotificationType.HOLIDAY);
        sn.setEnabled(true);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    // ===== 通用测试 =====

    @Test
    void shouldSkipDisabled() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 5);

        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(1L);
        sn.setTitle("已禁用");
        sn.setContent("不应该发送");
        sn.setFrequency(Frequency.DAILY);
        sn.setSendTime(LocalTime.of(9, 0));
        sn.setType(NotificationType.DAILY);
        sn.setEnabled(false);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of());
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
    }
}
