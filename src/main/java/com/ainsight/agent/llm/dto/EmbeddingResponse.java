package com.ainsight.agent.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * /embeddings 响应体:data 里每个元素是一条文本的向量。
 * 注意用 index 与输入顺序对齐,不要假设返回顺序。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingResponse {

    private List<Item> data;

    private String model;

    private Usage usage;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        private Integer index;

        private List<Float> embedding;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
