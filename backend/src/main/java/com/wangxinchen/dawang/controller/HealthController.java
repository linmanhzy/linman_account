package com.wangxinchen.dawang.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    // 健康检查接口：用来验证后端是否成功启动、能否连上数据库
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "记账大王-backend"
        );
    }
}
