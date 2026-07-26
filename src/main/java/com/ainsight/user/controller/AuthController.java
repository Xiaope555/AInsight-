package com.ainsight.user.controller;

import com.ainsight.common.result.Result;
import com.ainsight.infra.ratelimit.LimitTarget;
import com.ainsight.infra.ratelimit.RateLimit;
import com.ainsight.user.dto.LoginRequest;
import com.ainsight.user.dto.LoginResponse;
import com.ainsight.user.dto.RegisterRequest;
import com.ainsight.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-认证接口", description = "注册 / 登录 / 退出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.ok();
    }

    @Operation(summary = "登录", description = "成功返回 JWT,后续请求带 Authorization: Bearer <token>")
    // 未登录场景按 IP 限流:每分钟最多 5 次,给暴力破解加一道闸
    @RateLimit(key = "login", target = LimitTarget.IP, windowSeconds = 60, maxCount = 5)
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @Operation(summary = "退出登录", description = "将当前令牌加入 Redis 黑名单,立即失效")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        userService.logout(request.getHeader(HttpHeaders.AUTHORIZATION));
        return Result.ok();
    }
}
