package com.ainsight.agent.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 发给模型的"工具说明书"(OpenAI 协议 tools 数组的元素)。
 * parameters 是一份 JSON Schema:描述参数的结构、类型、含义。
 * 模型完全靠 name/description/parameters 里的文字理解工具怎么用 ——
 * 所以 description 写得好坏,直接决定模型调用的准确率。
 */
@Data
@AllArgsConstructor
public class ToolDefinition {

    /** 目前恒为 "function" */
    private String type;

    private FunctionDef function;

    public static ToolDefinition of(String name, String description, Map<String, Object> parametersSchema) {
        return new ToolDefinition("function", new FunctionDef(name, description, parametersSchema));
    }

    @Data
    @AllArgsConstructor
    public static class FunctionDef {

        private String name;

        private String description;

        /** JSON Schema,如 {"type":"object","properties":{...},"required":[...]} */
        private Map<String, Object> parameters;
    }
}
