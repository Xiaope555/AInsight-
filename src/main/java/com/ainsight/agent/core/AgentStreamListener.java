package com.ainsight.agent.core;

/**
 * 流式执行过程的事件回调:让调用方(SSE 层)实时感知 Agent 的每个动作。
 * 完成与异常不走回调 —— executeStream 同步返回 AgentResult / 抛异常,由调用方处理。
 */
public interface AgentStreamListener {

    /** 模型决定调用某个工具(参数已重组完整) */
    void onToolCall(String toolName, String arguments);

    /** 工具执行完毕 */
    void onToolResult(AgentStep step);

    /** 最终回答的增量片段(打字机的一个"字") */
    void onDelta(String delta);
}
