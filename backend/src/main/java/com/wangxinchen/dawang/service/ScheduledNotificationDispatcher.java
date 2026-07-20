package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.entity.Frequency;
import com.wangxinchen.dawang.entity.ScheduledNotification;
import com.wangxinchen.dawang.repository.ScheduledNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class ScheduledNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledNotificationDispatcher.class);

    /**
     * 触发窗口：仅在 sendTime 起 SEND_WINDOW_MINUTES 分钟内扫描到才派发。
     * 避免「晚上设置 7:30、下一分钟立即发出」以及「晚于设定时间就补发」的问题。
     */
    private static final int SEND_WINDOW_MINUTES = 2;

    private final ScheduledNotificationRepository scheduledNotificationRepository;
    private final NotificationService notificationService;

    public ScheduledNotificationDispatcher(ScheduledNotificationRepository scheduledNotificationRepository,
                                           NotificationService notificationService) {
        this.scheduledNotificationRepository = scheduledNotificationRepository;
        this.notificationService = notificationService;
    }

    /**
     * 每分钟扫描一次定时通知配置，触发到点的通知
     */
    @Scheduled(fixedDelay = 60_000)
    public void dispatchDueNotifications() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        dispatchDue(today, now);
    }

    /**
     * 供测试直接调用，传入固定的日期和时间
     */
    void dispatchDue(LocalDate today, LocalDateTime now) {
        List<ScheduledNotification> configs = scheduledNotificationRepository.findByEnabledTrue();
        for (ScheduledNotification sn : configs) {
            // ONCE 频率的通知仅在创建时立即发送，跳过定时调度（兜底保护）
            if (sn.getFrequency() == Frequency.ONCE) {
                continue;
            }
            if (!isDueToday(sn, today, now)) {
                continue;
            }
            // 同日不重复发送
            if (today.equals(sn.getLastFireDate())) {
                continue;
            }
            try {
                log.info("派发定时通知: id={} title={}", sn.getId(), sn.getTitle());
                notificationService.sendScheduledToAll(sn);
                sn.setLastFireDate(today);
                sn.setUpdatedAt(LocalDateTime.now());
                scheduledNotificationRepository.save(sn);
            } catch (Exception e) {
                log.error("派发定时通知失败: id={} error={}", sn.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 判断通知是否应在今天当前时刻触发。
     * DAILY: 检查当前时间是否已过 sendTime。
     * SPECIFIC_DATE: 检查今天是否等于 sendDate 且当前时间已过 sendTime。
     */
    private boolean isDueToday(ScheduledNotification sn, LocalDate today, LocalDateTime now) {
        if (sn.getSendTime() == null) {
            log.warn("定时通知缺少 sendTime: id={}", sn.getId());
            return false;
        }
        LocalTime st = sn.getSendTime();

        if (sn.getFrequency() == Frequency.SPECIFIC_DATE) {
            LocalDate sd = sn.getSendDate();
            if (sd == null) {
                log.warn("SPECIFIC_DATE 通知缺少 sendDate: id={}", sn.getId());
                return false;
            }
            // 今天不是指定日期，不触发
            if (!today.equals(sd)) {
                return false;
            }
        }
        // DAILY 或 SPECIFIC_DATE（今天匹配）：仅在 sendTime 起 SEND_WINDOW_MINUTES 窗口内触发
        LocalTime t = now.toLocalTime();
        return !t.isBefore(st) && t.isBefore(st.plusMinutes(SEND_WINDOW_MINUTES));
    }
}
