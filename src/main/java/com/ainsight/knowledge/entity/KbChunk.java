package com.ainsight.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片:content 是文本,embedding 是向量的 JSON(float 数组)。
 * MySQL 只负责持久化,检索发生在内存(InMemoryVectorStore)。
 */
@Data
@TableName("kb_chunk")
public class KbChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    /** 在原文档中的序号 */
    private Integer chunkIndex;

    private String content;

    /** 向量 JSON,如 [0.0123, -0.0456, ...],维度由 Embedding 模型决定 */
    private String embedding;

    private LocalDateTime createdAt;
}
