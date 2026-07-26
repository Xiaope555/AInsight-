package com.ainsight.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "语义检索命中结果")
public record SearchResultItem(
        @Schema(description = "所属文档ID") Long documentId,
        @Schema(description = "文档名") String docName,
        @Schema(description = "切片内容") String content,
        @Schema(description = "余弦相似度(0~1,越大越相关)") double score) {
}
