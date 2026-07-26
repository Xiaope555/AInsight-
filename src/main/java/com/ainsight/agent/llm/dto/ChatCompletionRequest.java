package com.ainsight.agent.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /chat/completions 请求体(OpenAI 协议)。
 * NON_NULL:为 null 的字段不序列化进 JSON,保持请求干净。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {

    private String model;

    /** 完整对话历史:LLM 无状态,每次都要全量携带 */
    private List<ChatMessage> messages;

    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** false=等完整回复;true=SSE 流式(阶段6) */
    private Boolean stream;

    /** 阶段4:工具说明书列表。带上它,模型才"知道自己有哪些手脚" */
    private List<ToolDefinition> tools;

    /** 一般不设(null=auto,由模型自主决定);"none" 可强制模型只输出文本 */
    @JsonProperty("tool_choice")
    private String toolChoice;
}
