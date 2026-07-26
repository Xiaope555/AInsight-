package com.ainsight.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功响应。用 record:不可变数据载体,一行顶一个类。
 */
@Schema(description = "登录成功响应")
public record LoginResponse(
        @Schema(description = "JWT 访问令牌") String token,
        @Schema(description = "令牌类型,请求时拼在 Authorization 头", example = "Bearer") String tokenType,
        @Schema(description = "有效期(秒)") long expiresInSeconds,
        @Schema(description = "用户ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "角色") String role) {
}
