package com.ainsight.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

/** Agent 执行轨迹中的一步(一次工具调用),返回给前端展示"思考过程" */
@Schema(description = "Agent 执行轨迹的一步")
public record AgentStep(
        @Schema(description = "第几轮 LLM 调用") int round,
        @Schema(description = "工具名") String toolName,
        @Schema(description = "模型生成的调用参数(JSON)") String arguments,
        @Schema(description = "工具返回的观察结果(截断展示)") String observation,
        @Schema(description = "工具耗时(毫秒)") long costMs,
        @Schema(description = "是否执行成功") boolean success) {
}
