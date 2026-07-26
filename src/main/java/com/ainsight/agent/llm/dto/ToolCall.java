package com.ainsight.agent.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型发起的一次工具调用(阶段4的主角,阶段3先就位)。
 * 注意 function.arguments 是【JSON 字符串】而不是 JSON 对象 —— 这是 OpenAI 协议的约定,
 * 执行前需要自己反序列化,这也是新手最容易踩的协议细节之一。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    /** 本次调用的唯一 id,role=tool 的结果消息要靠它配对 */
    private String id;

    /** 目前恒为 "function" */
    private String type;

    private FunctionCall function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {

        /** 工具名 */
        private String name;

        /** 调用参数,JSON 字符串,如 {"orderNo":"20260726001"} */
        private String arguments;
    }
}
