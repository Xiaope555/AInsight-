package com.ainsight.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器:每个请求执行一次。
 * 职责:尝试从 Authorization 头解析令牌 -> 校验签名/有效期/黑名单 -> 构建认证对象放入 SecurityContext。
 * 关键原则:这里【绝不抛异常】。令牌无效就保持"未认证"状态放行,
 * 由 Security 的 ExceptionTranslationFilter 统一走 AuthenticationEntryPoint 返回 401 JSON。
 *
 * 注意:本类故意不加 @Component —— Spring Boot 会把容器里的 Filter Bean 自动注册进
 * Servlet 过滤器链,导致它在安全链之外再跑一遍。我们只在 SecurityConfig 里手动 new。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtUtil.parse(token);
                boolean blacklisted = Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey(BLACKLIST_KEY_PREFIX + claims.getId()));
                if (blacklisted) {
                    log.debug("token in blacklist, jti={}", claims.getId());
                } else {
                    LoginUser loginUser = new LoginUser(
                            Long.valueOf(claims.getSubject()),
                            claims.get("username", String.class),
                            claims.get("role", String.class));
                    // hasRole("ADMIN") 匹配的是权限串 "ROLE_ADMIN",前缀必须自己拼
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.role()));
                    var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 签名错 / 过期 / 格式非法:不抛出,留给 EntryPoint 统一 401
                log.debug("invalid jwt: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
