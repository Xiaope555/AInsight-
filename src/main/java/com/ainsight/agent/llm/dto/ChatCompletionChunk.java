package com.ainsight.agent.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 流式响应的一个分片(SSE 每个 data 事件的内容)。
 * 与非流式的区别:message 变成了 delta —— 每片只携带"增量":
 *   - 普通回答:delta.content 是新生成的几个字;
 *   - 工具调用:delta.tool_calls 是被切碎的调用请求,同一调用的分片共享 index,
 *     function.arguments 是 JSON 字符串的碎片,必须逐段拼接后才是完整参数。
 * 流结束的标志是一条内容为 [DONE] 的事件。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionChunk {

    private String id;

    private String model;

    private List<ChunkChoice> choices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChunkChoice {

        private Integer index;

        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {

        private String role;

        private String content;

        @JsonProperty("tool_calls")
        private List<DeltaToolCall> toolCalls;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeltaToolCall {

        /** 同一次工具调用的所有分片共享同一个 index,靠它归并 */
        private Integer index;

        private String id;

        private String type;

        private DeltaFunction function;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeltaFunction {

        private String name;

        /** JSON 字符串碎片,如 {"or 、derNo":"2026 ... 需要拼接 */
        private String arguments;
    }
}
