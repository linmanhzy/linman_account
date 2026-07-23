package com.wangxinchen.dawang.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试（纯函数，不启 Spring 容器）
 *
 * 测试策略：
 * - 用反射注入 secret / expirationMs，绕开 @Value
 * - 签发 → 解析 必须能往返（subject/claims/role）
 * - 过期、篡改、密钥错配 必须失败（JwtException）
 *
 * 为何 TDD：登录/接口鉴权都依赖这里；secret 太短会启动失败（项目已隐式验过），
 * 但具体签发/解析的字段契约没人钉住，回归一次就够喝一壶。
 */
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-long-enough-for-hmac-sha256-algorithm-32+";
    private static final long EXPIRATION_MS = 60_000L; // 1 分钟，够测试用

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_shouldEmbedUserIdUsernameAndRole() {
        String token = jwtUtil.generateToken(42L, "alice", "USER");

        Claims claims = jwtUtil.parse(token);
        assertEquals("alice", claims.getSubject());
        assertEquals(42L, claims.get("userId", Long.class));
        assertEquals("USER", claims.get("role", String.class));
    }

    @Test
    void generateToken_shouldHaveValidExpirationInFuture() {
        long now = System.currentTimeMillis();
        String token = jwtUtil.generateToken(1L, "bob", "ADMIN");

        Claims claims = jwtUtil.parse(token);
        Date exp = claims.getExpiration();
        assertNotNull(exp);
        assertTrue(exp.getTime() > now, "过期时间应晚于当前时间");
        assertTrue(exp.getTime() - now <= EXPIRATION_MS + 1000, "过期时间不应超出 expirationMs 太多");
    }

    @Test
    void getUsername_shouldReturnSubject() {
        String token = jwtUtil.generateToken(1L, "carol", "USER");
        assertEquals("carol", jwtUtil.getUsername(token));
    }

    @Test
    void getUserId_shouldReturnLongClaim() {
        String token = jwtUtil.generateToken(999L, "dan", "USER");
        assertEquals(999L, jwtUtil.getUserId(token));
    }

    @Test
    void getRole_shouldReturnStringClaim() {
        String token = jwtUtil.generateToken(1L, "erin", "ADMIN");
        assertEquals("ADMIN", jwtUtil.getRole(token));
    }

    @Test
    void parse_invalidToken_shouldThrowJwtException() {
        // 篡改 token 末位字符 → 签名校验失败
        String bad = jwtUtil.generateToken(1L, "frank", "USER");
        String tampered = bad.substring(0, bad.length() - 1) + (bad.charAt(bad.length() - 1) == 'A' ? 'B' : 'A');

        assertThrows(JwtException.class, () -> jwtUtil.parse(tampered));
    }

    @Test
    void parse_wrongSecret_shouldThrowJwtException() {
        // 用 secret-A 签发，用 secret-B 解析 → 必须失败
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "completely-different-secret-also-long-enough-for-hmac-sha256");
        ReflectionTestUtils.setField(other, "expirationMs", EXPIRATION_MS);
        String token = other.generateToken(1L, "gary", "USER");

        assertThrows(JwtException.class, () -> jwtUtil.parse(token));
    }
}
