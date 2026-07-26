package com.ainsight.common.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 统一分页响应结构 */
@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "当前页数据") List<T> records,
        @Schema(description = "总条数") long total,
        @Schema(description = "当前页码") long current,
        @Schema(description = "每页条数") long size) {
}
