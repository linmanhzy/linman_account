package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.AuthResponse;
import com.wangxinchen.dawang.dto.LoginRequest;
import com.wangxinchen.dawang.dto.RegisterRequest;
import com.wangxinchen.dawang.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册新用户（默认普通用户）")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @Operation(summary = "登录并获取 JWT")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }
}
