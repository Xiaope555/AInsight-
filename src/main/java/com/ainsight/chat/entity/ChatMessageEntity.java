package com.ainsight.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息持久化实体(冷数据,全量)。
 * 命名加 Entity 后缀以区分协议 DTO(com.ainsight.agent.llm.dto.ChatMessage)。
 * 当前落库 user/assistant 文本消息;tool_calls/tool_call_id 列为完整轨迹回放预留
 * (工具轨迹已完整记录在 agent_tool_log —— 各表职责单一)。
 */
@Data
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant / tool / system */
    private String role;

    private String content;

    /** JSON 列,预留 */
    private String toolCalls;

    private String toolCallId;

    private Integer tokenUsage;

    private LocalDateTime createdAt;
}
