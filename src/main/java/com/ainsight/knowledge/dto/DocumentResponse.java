package com.ainsight.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "知识库文档信息")
public record DocumentResponse(
        @Schema(description = "文档ID") Long id,
        @Schema(description = "文档名") String name,
        @Schema(description = "类型 txt/md/pdf") String fileType,
        @Schema(description = "大小(字节)") Long fileSize,
        @Schema(description = "状态 PROCESSING/READY/FAILED") String status,
        @Schema(description = "切片数量") Integer chunkCount,
        @Schema(description = "上传时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
}
