package com.linman.account.controller;

import com.linman.account.common.Result;
import com.linman.account.dto.AuthResponse;
import com.linman.account.dto.LoginRequest;
import com.linman.account.dto.RegisterRequest;
import com.linman.account.service.AuthService;
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
