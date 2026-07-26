package com.ainsight.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "单轮对话请求")
public class SimpleChatRequest {

    @Schema(description = "用户问题", example = "用一句话解释什么是 JWT")
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最长 2000 字")
    private String message;
}
