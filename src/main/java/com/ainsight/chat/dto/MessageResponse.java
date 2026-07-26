package com.ainsight.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "历史消息")
public record MessageResponse(
        @Schema(description = "消息ID") Long id,
        @Schema(description = "user / assistant") String role,
        @Schema(description = "内容") String content,
        @Schema(description = "时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
}
