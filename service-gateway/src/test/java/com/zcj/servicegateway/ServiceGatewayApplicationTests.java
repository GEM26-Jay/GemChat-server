package com.zcj.servicegateway;

import com.zcj.common.utils.JWTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ServiceGatewayApplicationTests {

    private final JWTUtil jwtUtil;

    // 构造函数注入JWTUtil，使用测试专用密钥和较短有效期(10秒)
    ServiceGatewayApplicationTests() {
        // 测试用密钥(至少32字节)
        String testSecret = "testSecretKeyWithEnoughLengthForHS256Algorithm!";
        // 测试用有效期10秒
        long testTtl = 10 * 1000L;
        this.jwtUtil = new JWTUtil(testSecret, testTtl);
    }

    @Test
    void testJWTGenerationAndValidation() {
        // 1. 准备测试声明
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", "123456");
        claims.put("username", "testUser");
        claims.put("role", "ADMIN");

        // 2. 生成JWT令牌
        String token = jwtUtil.create(claims);
        assertNotNull(token, "生成的JWT令牌不能为null");
        assertFalse(token.isEmpty(), "生成的JWT令牌不能为空字符串");

        // 3. 验证令牌有效性
        assertTrue(jwtUtil.validate(token), "刚生成的令牌应该验证通过");

        // 4. 验证后读取声明
        Map<String, Object> payload = jwtUtil.getPayloadIfValid(token);
        assertNotNull(payload.toString(), "验证通过的令牌应能获取到Payload");
        assertEquals("123456", payload.get("userId"), "userId声明值不匹配");
        assertEquals("testUser", payload.get("username"), "username声明值不匹配");

        // 5. 验证后读取特定声明
        String role = jwtUtil.getClaimIfValid(token, "role");
        assertEquals("ADMIN", role, "role声明值不匹配");

        // 6. 测试不安全读取方法(仅用于验证方法功能)
        Map<String, Object> unsafePayload = jwtUtil.getPayloadUnsafe(token);
        assertEquals(claims.get("userId"), unsafePayload.get("userId"), "不安全读取的userId不匹配");
    }

    @Test
    void testInvalidJWT() {
        // 1. 测试空令牌
        assertFalse(jwtUtil.validate(""), "空字符串令牌应验证失败");
        assertNull(jwtUtil.getPayloadIfValid(""), "空字符串令牌不应获取到Payload");

        // 2. 测试无效格式令牌
        String invalidToken = "invalid.token.format";
        assertFalse(jwtUtil.validate(invalidToken), "无效格式令牌应验证失败");

        // 3. 测试被篡改的令牌
        String validToken = jwtUtil.create(new HashMap<>());
        // 简单篡改最后一个字符
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + "x";
        assertFalse(jwtUtil.validate(tamperedToken), "被篡改的令牌应验证失败");
    }

    @Test
    void testExpiredJWT() throws InterruptedException {
        // 1. 生成令牌
        String token = jwtUtil.create(new HashMap<>());
        assertTrue(jwtUtil.validate(token), "刚生成的令牌应该有效");

        // 2. 等待令牌过期(测试用令牌有效期10秒)
        System.out.println("等待令牌过期...");
        Thread.sleep(11000); // 等待11秒确保过期

        // 3. 验证过期令牌
        assertFalse(jwtUtil.validate(token), "过期的令牌应验证失败");
        assertNull(jwtUtil.getPayloadIfValid(token), "过期的令牌不应获取到Payload");
    }
}
