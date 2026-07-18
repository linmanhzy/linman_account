package com.linman.account.controller;

import com.linman.account.common.Result;
import com.linman.account.dto.NotificationResponse;
import com.linman.account.dto.UnreadCountResponse;
import com.linman.account.security.SecurityHelper;
import com.linman.account.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "查看自己的通知列表")
    @GetMapping
    public Result<List<NotificationResponse>> list() {
        return Result.ok(notificationService.myNotifications(SecurityHelper.getCurrentUserId()));
    }

    @Operation(summary = "获取未读通知数量")
    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount() {
        return Result.ok(notificationService.unreadCount(SecurityHelper.getCurrentUserId()));
    }

    @Operation(summary = "标记某条通知已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(SecurityHelper.getCurrentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(SecurityHelper.getCurrentUserId());
        return Result.ok();
    }
}
