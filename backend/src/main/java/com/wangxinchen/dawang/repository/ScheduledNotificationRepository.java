package com.wangxinchen.dawang.repository;

import com.wangxinchen.dawang.entity.ScheduledNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, Long> {
    List<ScheduledNotification> findByEnabledTrue();
}
