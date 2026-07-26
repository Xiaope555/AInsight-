package com.ainsight.agent.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * POST /embeddings 请求体(OpenAI 协议):把一批文本变成一批向量。
 */
@Data
@AllArgsConstructor
public class EmbeddingRequest {

    private String model;

    /** 待向量化的文本列表(单次条数有上限,分批由调用方控制) */
    private List<String> input;
}
