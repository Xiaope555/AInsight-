package com.ainsight.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "单轮对话响应")
public record SimpleChatResponse(
        @Schema(description = "模型回复") String reply,
        @Schema(description = "实际使用的模型") String model,
        @Schema(description = "输入消耗 token") Integer promptTokens,
        @Schema(description = "输出消耗 token") Integer completionTokens,
        @Schema(description = "总消耗 token") Integer totalTokens,
        @Schema(description = "耗时(毫秒)") long costMs) {
}
