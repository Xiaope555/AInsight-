package com.ainsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型接入配置,对应 application.yml 的 ainsight.llm.*
 * 全部走 OpenAI 兼容协议:换模型厂商只需要改 base-url / api-key / 模型名。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ainsight.llm")
public class LlmProperties {

    /** OpenAI 兼容 API 根地址(不含 /chat/completions) */
    private String baseUrl;

    /** 通过环境变量注入,严禁把真实 Key 提交到 Git */
    private String apiKey;

    /** 对话模型名 */
    private String chatModel;

    /** 向量模型名(阶段5使用) */
    private String embeddingModel;

    /** 单次调用最长等待秒数(LLM 生成可能较慢,别设太短) */
    private int timeoutSeconds = 60;

    /** 采样温度:0 最确定,越高越发散;对话 0.7 左右,工具调用/分类任务建议更低 */
    private Double temperature = 0.7;

    /** Agent 工具调用场景的温度:调低,让模型严谨地选工具、填参数 */
    private Double agentTemperature = 0.2;
}
