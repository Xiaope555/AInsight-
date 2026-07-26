package com.ainsight.config;

import com.ainsight.security.JwtAuthenticationFilter;
import com.ainsight.security.JwtUtil;
import com.ainsight.security.RestAccessDeniedHandler;
import com.ainsight.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 主配置:无状态 JWT 方案。
 * 请求旅程:JwtAuthenticationFilter(解析令牌) -> 授权规则判定 ->
 *   未认证 -> RestAuthenticationEntryPoint(401 JSON)
 *   权限不足 -> RestAccessDeniedHandler(403 JSON)
 *   通过 -> Controller
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启 @PreAuthorize 方法级权限
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    /** 无需登录即可访问的路径 */
    private static final String[] WHITELIST = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/demo/**",
            // API docs
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 无 Cookie 无 Session,CSRF 攻击面不存在,关闭之
                .csrf(AbstractHttpConfigurer::disable)
                // 绝不创建 HttpSession,认证状态完全由每次请求携带的 JWT 决定
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITELIST).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // 手动 new(而不是声明成 Bean):避免 Spring Boot 把 Filter Bean
                // 自动注册进 Servlet 链,造成安全链之外的重复执行
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, stringRedisTemplate),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
