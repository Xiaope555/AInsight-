package com.ainsight.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "用户信息")
public record UserInfoResponse(
        @Schema(description = "用户ID") Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "角色") String role,
        @Schema(description = "注册时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
}
