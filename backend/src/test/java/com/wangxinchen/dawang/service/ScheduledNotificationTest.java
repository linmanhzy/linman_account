package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.entity.Frequency;
import com.wangxinchen.dawang.entity.NotificationType;
import com.wangxinchen.dawang.entity.ScheduledNotification;
import com.wangxinchen.dawang.repository.ScheduledNotificationRepository;
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

    private ScheduledNotification daily(LocalTime sendTime) {
        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(1L);
        sn.setTitle("每日记账提醒");
        sn.setContent("记得今天的账哦！");
        sn.setFrequency(Frequency.DAILY);
        sn.setSendTime(sendTime);
        sn.setType(NotificationType.DAILY);
        sn.setEnabled(true);
        return sn;
    }

    private ScheduledNotification specificDate(LocalDate date, LocalTime sendTime) {
        ScheduledNotification sn = new ScheduledNotification();
        sn.setId(2L);
        sn.setTitle("国庆祝福");
        sn.setContent("国庆节快乐！");
        sn.setFrequency(Frequency.SPECIFIC_DATE);
        sn.setSendDate(date);
        sn.setSendTime(sendTime);
        sn.setType(NotificationType.HOLIDAY);
        sn.setEnabled(true);
        return sn;
    }

    // ===== 核心 bug 修复：晚设不立即发，仅 sendTime 起 2 分钟窗口内发 =====

    @Test
    void daily_firesWithinWindowAfterSendTime() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 1); // 9:00 后 1 分钟，窗口内

        ScheduledNotification sn = daily(LocalTime.of(9, 0));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, times(1)).sendScheduledToAll(sn);
        verify(scheduledNotificationRepository, times(1)).save(sn);
        assertEquals(today, sn.getLastFireDate());
    }

    @Test
    void daily_doesNotFireWhenSetLateSameDay() {
        // 用户晚上 20:00 设置了「每天 7:30 问候」—— 不应立即发出
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 20, 0);

        ScheduledNotification sn = daily(LocalTime.of(7, 30));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void daily_doesNotFireLongAfterSendTime() {
        // 9:05 已远超 2 分钟窗口，不应补发（避免「晚于设定时间就发」）
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 5);

        ScheduledNotification sn = daily(LocalTime.of(9, 0));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void daily_doesNotFireBeforeSendTime() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 8, 0);

        ScheduledNotification sn = daily(LocalTime.of(9, 0));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void daily_doesNotSendTwiceOnSameDay() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 1);

        ScheduledNotification sn = daily(LocalTime.of(9, 0));
        sn.setLastFireDate(today); // 今日已发

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    // ===== SPECIFIC_DATE =====

    @Test
    void specificDate_firesWithinWindowOnMatchDay() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 10, 1);

        ScheduledNotification sn = specificDate(LocalDate.of(2026, 10, 1), LocalTime.of(10, 0));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, times(1)).sendScheduledToAll(sn);
        verify(scheduledNotificationRepository, times(1)).save(sn);
        assertEquals(today, sn.getLastFireDate());
    }

    @Test
    void specificDate_doesNotFireOutsideWindowOnMatchDay() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 10, 30);

        ScheduledNotification sn = specificDate(LocalDate.of(2026, 10, 1), LocalTime.of(10, 0));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void specificDate_doesNotFireOnNonMatchDay() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 10, 1);

        ScheduledNotification sn = specificDate(LocalDate.of(2026, 10, 1), LocalTime.of(10, 0));
        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of(sn));
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
        verify(scheduledNotificationRepository, never()).save(any());
    }

    @Test
    void shouldSkipDisabled() {
        LocalDate today = LocalDate.of(2026, 1, 15);
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 1);

        ScheduledNotification sn = daily(LocalTime.of(9, 0));
        sn.setEnabled(false);

        when(scheduledNotificationRepository.findByEnabledTrue()).thenReturn(List.of());
        dispatcher.dispatchDue(today, now);

        verify(notificationService, never()).sendScheduledToAll(any());
    }
}
