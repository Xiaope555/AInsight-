package com.ainsight.agent.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 协议的消息对象 —— 整个 LLM 交互的最小单元。
 * 四种角色:
 *   system    人设与规则(第一条)
 *   user      用户发言
 *   assistant 模型发言(可能是文本,也可能是"我要调工具"的 tool_calls)
 *   tool      工具执行结果(必须带 tool_call_id 与调用配对)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    /** system / user / assistant / tool */
    private String role;

    /** 文本内容;assistant 只发起工具调用时可为 null */
    private String content;

    /** 阶段4:assistant 消息中,模型发起的工具调用列表 */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** 阶段4:role=tool 时,对应 assistant 那次调用的 id */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    /** 工具执行结果消息(阶段4使用) */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, toolCallId);
    }
}
