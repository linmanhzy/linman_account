package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.NotificationResponse;
import com.wangxinchen.dawang.dto.UnreadCountResponse;
import com.wangxinchen.dawang.security.SecurityHelper;
import com.wangxinchen.dawang.service.NotificationService;
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
