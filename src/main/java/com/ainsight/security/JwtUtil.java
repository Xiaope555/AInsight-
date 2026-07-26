package com.ainsight.security;

import com.ainsight.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 生成与解析(jjwt 0.12.x API)。
 * payload 只放 userId/username/role 这类非敏感信息 —— JWT 的 payload 是可解码的,不是加密的!
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties properties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        // 少于 32 字节这里会抛 WeakKeyException,启动即失败,好过线上被弱密钥
        key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti:令牌唯一ID,退出登录拉黑时用
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.getExpireMinutes() * 60_000))
                .signWith(key)                      // 32 字节密钥 => 自动选 HS256
                .compact();
    }

    /** 解析并校验签名与有效期;签名不对/过期/格式非法会抛 JwtException 子类 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getExpireSeconds() {
        return properties.getExpireMinutes() * 60;
    }
}
