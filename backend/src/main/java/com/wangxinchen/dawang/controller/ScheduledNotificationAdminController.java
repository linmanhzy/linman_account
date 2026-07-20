package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.ScheduledNotificationRequest;
import com.wangxinchen.dawang.entity.Frequency;
import com.wangxinchen.dawang.entity.NotificationType;
import com.wangxinchen.dawang.entity.ScheduledNotification;
import com.wangxinchen.dawang.repository.ScheduledNotificationRepository;
import com.wangxinchen.dawang.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/scheduled-notifications")
@PreAuthorize("hasRole('ADMIN')")
public class ScheduledNotificationAdminController {

    private final ScheduledNotificationRepository repo;
    private final NotificationService notificationService;

    public ScheduledNotificationAdminController(ScheduledNotificationRepository repo,
                                                 NotificationService notificationService) {
        this.repo = repo;
        this.notificationService = notificationService;
    }

    /**
     * 获取所有定时通知配置
     */
    @Operation(summary = "列出所有定时通知配置")
    @GetMapping
    public Result<List<ScheduledNotification>> list() {
        return Result.ok(repo.findAll());
    }

    /**
     * 创建新的定时通知
     */
    @Operation(summary = "创建定时通知（ONCE 频率立即发送，其他频率进入调度）")
    @PostMapping
    public Result<ScheduledNotification> create(@Valid @RequestBody ScheduledNotificationRequest req) {
        ScheduledNotification sn = new ScheduledNotification();
        sn.setTitle(req.getTitle());
        sn.setContent(req.getContent());
        sn.setFrequency(req.getFrequency());
        sn.setSendTime(req.getSendTime());
        sn.setSendDate(req.getSendDate());
        sn.setType(req.getType() != null ? req.getType() : NotificationType.DAILY);
        sn.setTargetUserId(req.getTargetUserId());
        sn.setCreatedAt(LocalDateTime.now());

        // ONCE 频率：立即通过 NotificationService 派发，然后保存为 disabled 记录（不进入定时调度）
        if (req.getFrequency() == Frequency.ONCE) {
            notificationService.sendScheduledToAll(sn);
            sn.setEnabled(false);
        } else {
            sn.setEnabled(true);
        }

        return Result.ok(repo.save(sn));
    }

    /**
     * 修改定时通知
     */
    @Operation(summary = "修改定时通知")
    @PutMapping("/{id}")
    public Result<ScheduledNotification> update(@PathVariable Long id,
                                                @Valid @RequestBody ScheduledNotificationRequest req) {
        ScheduledNotification sn = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("定时通知不存在: " + id));
        sn.setTitle(req.getTitle());
        sn.setContent(req.getContent());
        sn.setFrequency(req.getFrequency());
        // ONCE 频率：与 create 一致，保存为 disabled 记录（被列表过滤、调度器跳过、不重发），避免“永不触发却显示已发送”的幽灵记录
        if (Frequency.ONCE.equals(req.getFrequency())) {
            sn.setEnabled(false);
        }
        sn.setSendTime(req.getSendTime());
        sn.setSendDate(req.getSendDate());
        sn.setType(req.getType() != null ? req.getType() : NotificationType.DAILY);
        sn.setTargetUserId(req.getTargetUserId());
        sn.setUpdatedAt(LocalDateTime.now());
        return Result.ok(repo.save(sn));
    }

    /**
     * 切换启用/禁用
     */
    @Operation(summary = "切换启用/禁用状态")
    @PutMapping("/{id}/toggle")
    public Result<ScheduledNotification> toggle(@PathVariable Long id) {
        ScheduledNotification sn = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("定时通知不存在: " + id));
        sn.setEnabled(!sn.getEnabled());
        sn.setUpdatedAt(LocalDateTime.now());
        return Result.ok(repo.save(sn));
    }

    /**
     * 删除定时通知
     */
    @Operation(summary = "删除定时通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            throw new RuntimeException("定时通知不存在: " + id);
        }
        repo.deleteById(id);
        return Result.ok();
    }
}
