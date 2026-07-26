package com.ainsight.user.controller;

import com.ainsight.common.result.Result;
import com.ainsight.security.SecurityUtils;
import com.ainsight.user.dto.UserInfoResponse;
import com.ainsight.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02-用户接口", description = "需要登录")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public Result<UserInfoResponse> me() {
        return Result.ok(userService.me());
    }

    @Operation(summary = "管理员专属演示接口", description = "验证 @PreAuthorize 角色控制,USER 访问返回 403")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/ping")
    public Result<String> adminPing() {
        return Result.ok("hello, admin " + SecurityUtils.getLoginUser().username());
    }
}
