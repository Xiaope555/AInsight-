package com.ainsight.user.service;

import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.ResultCode;
import com.ainsight.security.JwtAuthenticationFilter;
import com.ainsight.security.JwtUtil;
import com.ainsight.security.SecurityUtils;
import com.ainsight.user.dto.LoginRequest;
import com.ainsight.user.dto.LoginResponse;
import com.ainsight.user.dto.RegisterRequest;
import com.ainsight.user.dto.UserInfoResponse;
import com.ainsight.user.entity.SysUser;
import com.ainsight.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public void register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        // BCrypt:自动加盐的慢哈希,同一密码每次结果都不同
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        log.info("new user registered: {}", user.getUsername());
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        // 安全细节:用户不存在和密码错误返回同一个提示,不给撞库者"用户名是否存在"的信号
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, "Bearer", jwtUtil.getExpireSeconds(),
                user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    /**
     * 退出登录:JWT 本身无法作废,把 jti 放进 Redis 黑名单,TTL = 令牌剩余寿命。
     * 令牌自然过期后黑名单键也随 TTL 消失,状态量始终很小。
     */
    public void logout(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        try {
            Claims claims = jwtUtil.parse(authorizationHeader.substring(7));
            long remainMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainMillis > 0) {
                stringRedisTemplate.opsForValue().set(
                        JwtAuthenticationFilter.BLACKLIST_KEY_PREFIX + claims.getId(),
                        "1", Duration.ofMillis(remainMillis));
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 令牌本就非法或已过期,无需拉黑
        }
    }

    /** 当前用户信息:查库取最新数据(claims 里的昵称等可能已过时) */
    public UserInfoResponse me() {
        SysUser user = userMapper.selectById(SecurityUtils.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return new UserInfoResponse(user.getId(), user.getUsername(),
                user.getNickname(), user.getRole(), user.getCreatedAt());
    }
}
