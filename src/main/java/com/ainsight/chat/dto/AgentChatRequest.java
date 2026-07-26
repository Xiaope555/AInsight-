package com.ainsight.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Agent 对话请求")
public class AgentChatRequest {

    @Schema(description = "用自然语言描述任务",
            example = "帮我查一下订单 20260726001 的状态,再告诉我北京今天的天气")
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最长 2000 字")
    private String question;
}
