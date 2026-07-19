package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.entity.*;
import com.wangxinchen.dawang.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 违规追踪：记录用户违规操作，一周内达到阈值时通知所有管理员。
 * 超过 7 天窗口的旧违规记录会被重置。
 */
@Service
public class ViolationService {

    private static final int WINDOW_DAYS = 7;
    private static final int NOTIFY_THRESHOLD = 2;

    private final UserViolationRepository violationRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notificationRepo;

    public ViolationService(UserViolationRepository violationRepo,
                            UserRepository userRepo,
                            NotificationRepository notificationRepo) {
        this.violationRepo = violationRepo;
        this.userRepo = userRepo;
        this.notificationRepo = notificationRepo;
    }

    /**
     * 记录一次违规，若窗口期内达到阈值则通知管理员。
     *
     * @return 应返回给前端用户的提示语
     */
    public String recordAndCheck(Long userId, String username, String violationType) {
        UserViolation uv = violationRepo.findByUserIdAndViolationType(userId, violationType)
                .orElseGet(() -> {
                    UserViolation n = new UserViolation();
                    n.setUserId(userId);
                    n.setViolationType(violationType);
                    n.setCount(0);
                    n.setNotified(false);
                    return n;
                });

        LocalDateTime now = LocalDateTime.now();

        // 超过窗口期则重置
        if (uv.getFirstAt() != null
                && ChronoUnit.DAYS.between(uv.getFirstAt(), now) > WINDOW_DAYS) {
            uv.setCount(0);
            uv.setNotified(false);
        }

        uv.setCount(uv.getCount() + 1);
        if (uv.getFirstAt() == null) {
            uv.setFirstAt(now);
        }
        uv.setLastAt(now);
        violationRepo.save(uv);

        // 达到阈值且未通知管理员 → 发危险通知
        if (uv.getCount() >= NOTIFY_THRESHOLD && !uv.isNotified()) {
            notifyAdmins(userId, username, violationType, uv.getCount());
            uv.setNotified(true);
            violationRepo.save(uv);
            return "参数异常：你的违规操作已被记录并通知管理员，请遵守规范。";
        }
        return "参数异常：你的违规操作已被记录，请注意规范操作。";
    }

    private void notifyAdmins(Long violatorId, String violatorName,
                              String violationType, int times) {
        List<User> admins = userRepo.findByRole(Role.ADMIN);
        LocalDateTime now = LocalDateTime.now();
        String title = "⚠️ 用户违规提醒";
        String content = String.format(
                "用户 %s（ID: %d）在一周内第 %d 次触发违规操作（类型：%s），请关注审查。",
                violatorName, violatorId, times, violationType);
        for (User admin : admins) {
            Notification n = new Notification();
            n.setUserId(admin.getId());
            n.setTitle(title);
            n.setContent(content);
            n.setIsRead(false);
            n.setCreatedAt(now);
            notificationRepo.save(n);
        }
    }
}
