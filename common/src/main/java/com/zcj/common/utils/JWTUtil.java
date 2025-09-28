package com.zcj.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Getter
public class JWTUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Key secretKey;
    private final long ttlMillis;

    public JWTUtil(String secretKey, Long ttlMillis) {
        // 验证密钥长度，HS256至少需要256位(32字节)
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Secret key must be at least 32 bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMillis;
    }

    /**
     * 生成JWT令牌
     */
    public String create(Map<String, Object> claims) {
        if (claims == null) {
            throw new IllegalArgumentException("Claims cannot be null");
        }
        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + ttlMillis;
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(nowMillis))
                .setExpiration(new Date(expMillis))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 验证JWT签名有效性
     */
    public boolean validate(String jwt) {
        return parseClaimsJws(jwt) != null;
    }

    /**
     * 验证并解析JWT，返回Claims对象
     */
    private Claims parseClaimsJws(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT已过期: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT验证失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 无验证读取(谨慎使用) ====================

    /**
     * 直接读取JWT的Payload（不验证签名，可能被篡改）
     * 注意：此方法仅用于特殊场景，一般应使用getPayloadIfValid()
     */
    public Map<String, Object> getPayloadUnsafe(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            throw new IllegalArgumentException("JWT不能为空");
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("无效的JWT格式");
            }
            String payloadBase64 = parts[1];
            String jsonPayload = new String(
                    Base64.getUrlDecoder().decode(payloadBase64),
                    StandardCharsets.UTF_8
            );
            return parseJsonToMap(jsonPayload);
        } catch (Exception e) {
            throw new RuntimeException("读取JWT Payload失败", e);
        }
    }

    /**
     * 从JWT中提取特定声明（不验证签名）
     * 注意：此方法仅用于特殊场景，一般应使用getClaimIfValid()
     */
    public <T> T getClaimUnsafe(String jwt, String claimName) {
        Map<String, Object> payload = getPayloadUnsafe(jwt);
        return (T) payload.get(claimName);
    }

    // ==================== 验证后读取 ====================

    /**
     * 验证JWT并读取Payload（验证失败返回null）
     */
    public Map<String, Object> getPayloadIfValid(String jwt) {
        Claims claims = parseClaimsJws(jwt);
        if (claims != null) {
            return new HashMap<>(claims);
        }
        return null;
    }

    /**
     * 验证JWT并提取特定声明（验证失败返回null）
     */
    public <T> T getClaimIfValid(String jwt, String claimName) {
        Claims claims = parseClaimsJws(jwt);
        if (claims != null) {
            return (T) claims.get(claimName);
        }
        return null;
    }

    /**
     * 获取令牌过期时间
     */
    public Date getExpirationIfValid(String jwt) {
        Claims claims = parseClaimsJws(jwt);
        return claims != null ? claims.getExpiration() : null;
    }

    // ==================== 辅助方法 ====================

    private static Map<String, Object> parseJsonToMap(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("解析JSON失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

}
