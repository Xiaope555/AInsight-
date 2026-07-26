package com.ainsight.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档。状态机:PROCESSING -> READY / FAILED(异步处理,前端轮询)。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传人 */
    private Long userId;

    private String name;

    /** txt / md / pdf */
    private String fileType;

    private Long fileSize;

    /** PROCESSING / READY / FAILED */
    private String status;

    private Integer chunkCount;

    /**
     * 逻辑删除:@TableLogic 后,MP 的 deleteById 变成 UPDATE deleted=1,
     * 所有查询自动追加 deleted=0 条件 —— 数据"看不见"但没消失,可追溯可恢复。
     */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
