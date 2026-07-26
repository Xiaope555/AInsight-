package com.ainsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话模块参数,对应 application.yml 的 ainsight.chat.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "ainsight.chat")
public class ChatProperties {

    /** Redis 热上下文保留的最大消息条数(一问一答算 2 条) */
    private int contextMaxMessages = 20;

    /** 热上下文 TTL:活跃会话常驻,冷会话自动过期,需要时再从 MySQL 重建 */
    private int contextTtlMinutes = 120;

    /** SSE 连接超时(Agent 多轮工具 + 流式生成可能较久) */
    private long sseTimeoutMinutes = 5;
}
