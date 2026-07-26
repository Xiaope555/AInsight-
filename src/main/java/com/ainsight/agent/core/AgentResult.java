package com.ainsight.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Agent 执行结果")
public record AgentResult(
        @Schema(description = "最终回答") String answer,
        @Schema(description = "工具调用轨迹") List<AgentStep> steps,
        @Schema(description = "LLM 调用轮数") int llmRounds,
        @Schema(description = "本次任务总 token 消耗") int totalTokens,
        @Schema(description = "总耗时(毫秒)") long costMs) {
}
