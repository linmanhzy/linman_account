package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开接口：返回当前系统版本号（从 application.yml myapp.version 读取）。
 * 前端调用后缓存渲染在侧栏 Logo 区域。
 */
@RestController
public class VersionController {

    @Value("${myapp.version:1.0.0}")
    private String version;

    @GetMapping("/api/version")
    public Result<String> version() {
        return Result.ok(version);
    }
}
