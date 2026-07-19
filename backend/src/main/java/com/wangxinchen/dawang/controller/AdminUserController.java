package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.*;
import com.wangxinchen.dawang.security.SecurityHelper;
import com.wangxinchen.dawang.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "管理员：查看所有用户")
    @GetMapping("/users")
    public Result<List<UserSummaryDto>> listUsers() {
        return Result.ok(userService.listUsers());
    }

    @Operation(summary = "管理员：创建用户")
    @PostMapping("/users")
    public Result<UserSummaryDto> createUser(@Valid @RequestBody CreateUserRequest req) {
        return Result.ok(userService.createUser(req));
    }

    @Operation(summary = "管理员：更新用户信息")
    @PutMapping("/users/{id}")
    public Result<UserSummaryDto> updateUser(@PathVariable Long id,
                                              @Valid @RequestBody UpdateUserRequest req) {
        Long operatorId = SecurityHelper.getCurrentUserId();
        return Result.ok(userService.updateUser(id, req, operatorId));
    }

    @Operation(summary = "管理员：删除用户")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        Long operatorId = SecurityHelper.getCurrentUserId();
        String operatorName = SecurityHelper.getCurrentUsername();
        userService.deleteUser(id, operatorId, operatorName);
        return Result.ok(null);
    }

    @Operation(summary = "管理员：重置用户密码")
    @PutMapping("/users/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                       @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req);
        return Result.ok(null);
    }

    @Operation(summary = "管理员：启用/禁用用户")
    @PutMapping("/users/{id}/status")
    public Result<UserSummaryDto> changeStatus(@PathVariable Long id,
                                               @Valid @RequestBody ChangeStatusRequest req) {
        Long operatorId = SecurityHelper.getCurrentUserId();
        return Result.ok(userService.changeStatus(id, req.getStatus(), operatorId));
    }

    @Operation(summary = "管理员：获取用户简要列表（id + username），用于通知选择器")
    @GetMapping("/users/simple")
    public Result<List<UserSimpleDto>> listSimpleUsers() {
        return Result.ok(userService.listSimpleUsers());
    }
}
