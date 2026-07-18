package com.linman.account.controller;

import com.linman.account.common.Result;
import com.linman.account.dto.ChangeStatusRequest;
import com.linman.account.dto.UserSummaryDto;
import com.linman.account.service.UserService;
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

    @Operation(summary = "管理员：启用/禁用用户")
    @PutMapping("/users/{id}/status")
    public Result<UserSummaryDto> changeStatus(@PathVariable Long id,
                                               @Valid @RequestBody ChangeStatusRequest req) {
        return Result.ok(userService.changeStatus(id, req.getStatus()));
    }
}
