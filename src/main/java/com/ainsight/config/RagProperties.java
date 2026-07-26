package com.ainsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 相关参数,对应 application.yml 的 ainsight.rag.*
 * 这些值都是"可调旋钮":切片大小/重叠影响检索粒度,top-k/min-score 影响召回质量。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ainsight.rag")
public class RagProperties {

    /** 每个切片的目标字符数 */
    private int chunkSize = 500;

    /** 相邻切片的重叠字符数(防止句子被拦腰切断丢语义) */
    private int chunkOverlap = 100;

    /** 检索返回的切片数量 */
    private int topK = 3;

    /** 相似度阈值:低于它的结果宁可不要(垃圾进 prompt 比没有更糟) */
    private double minScore = 0.35;

    /** 每次 Embedding API 调用的文本条数(百炼单次上限约 10) */
    private int embeddingBatchSize = 10;
}
