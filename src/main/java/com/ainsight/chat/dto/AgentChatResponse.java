package com.ainsight.chat.dto;

import com.ainsight.agent.core.AgentStep;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Agent 对话响应(非流式)")
public record AgentChatResponse(
        @Schema(description = "会话ID(请求未带时由服务端自动创建)") Long conversationId,
        @Schema(description = "最终回答") String answer,
        @Schema(description = "工具调用轨迹") List<AgentStep> steps,
        @Schema(description = "LLM 调用轮数") int llmRounds,
        @Schema(description = "总 token 消耗(流式模式为 0)") int totalTokens,
        @Schema(description = "总耗时(毫秒)") long costMs) {
}
