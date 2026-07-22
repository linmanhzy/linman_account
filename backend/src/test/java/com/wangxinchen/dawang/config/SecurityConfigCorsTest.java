package com.wangxinchen.dawang.config;

import com.wangxinchen.dawang.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证「CD 产出的安卓 APK 能否访问后端」的关键一环：
 * 后端 CORS 必须放行 Tauri WebView 的真实 Origin（https://tauri.localhost）。
 *
 * 这些是纯单元测试——直接构造 SecurityConfig、注入 allowedOrigins 字段，
 * 不启动 Spring 上下文，也不依赖数据库，专门钉住 CORS 解析逻辑。
 */
class SecurityConfigCorsTest {

    private static final String TAURI_ORIGIN = "https://tauri.localhost";

    /** 用反射把 allowedOrigins 字段设为指定值，返回构建出的 CORS 配置。 */
    private CorsConfiguration buildConfig(String allowedOrigins) throws Exception {
        SecurityConfig config = new SecurityConfig(Mockito.mock(JwtAuthenticationFilter.class));
        Field f = SecurityConfig.class.getDeclaredField("allowedOrigins");
        f.setAccessible(true);
        f.set(config, allowedOrigins);
        CorsConfigurationSource source = config.corsConfigurationSource();
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/login"));
    }

    /** 默认通配 * 时，Tauri 源必须被放行（内测约定 CORS_ORIGINS=*）。 */
    @Test
    void wildcardAllowsTauriOrigin() throws Exception {
        CorsConfiguration cc = buildConfig("*");
        assertEquals(TAURI_ORIGIN, cc.checkOrigin(TAURI_ORIGIN));
    }

    /** 显式列出 Tauri 源时，必须被放行。 */
    @Test
    void explicitListAllowsTauriOrigin() throws Exception {
        CorsConfiguration cc = buildConfig("https://tauri.localhost,http://47.104.152.25:8080");
        assertEquals(TAURI_ORIGIN, cc.checkOrigin(TAURI_ORIGIN));
    }

    /**
     * 关键回归：CORS_ORIGINS 未配置（docker-compose 透传空串）时，
     * 不得塌缩成空允许列表——否则所有源（含 Tauri App）都会被静默拒绝，
     * 表现为「health 能通、App 却登录失败」。应兜底为允许全部来源。
     */
    @Test
    void emptyOriginsFallsBackToAllowAll() throws Exception {
        CorsConfiguration cc = buildConfig("");
        assertNotNull(cc.checkOrigin(TAURI_ORIGIN),
                "CORS_ORIGINS 为空时不应拒绝 Tauri 源，否则 CD 产出的 APK 连不上后端");
    }
}
