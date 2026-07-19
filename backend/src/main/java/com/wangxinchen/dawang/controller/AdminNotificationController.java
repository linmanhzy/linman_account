package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.NotificationRequest;
import com.wangxinchen.dawang.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {
    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "管理员发送通知（全体或指定用户）")
    @PostMapping
    public Result<Void> send(@Valid @RequestBody NotificationRequest req) {
        if (req.getTargetUserId() == null) {
            notificationService.sendToAll(req);
        } else {
            notificationService.sendToUser(req.getTargetUserId(), req);
        }
        return Result.ok();
    }
}
