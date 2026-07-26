package com.ainsight.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具调用审计日志:排查问题的黑匣子,也是可观测性设计的体现。
 */
@Data
@TableName("agent_tool_log")
public class AgentToolLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 阶段4暂为占位 0,阶段6接入真实会话 id */
    private Long conversationId;

    private String toolName;

    /** 模型生成的调用参数(JSON 字符串,入 JSON 列) */
    private String arguments;

    /** 工具执行结果(超长截断) */
    private String result;

    /** 1=成功 0=失败 */
    private Integer success;

    private Integer costMs;

    private LocalDateTime createdAt;
}
