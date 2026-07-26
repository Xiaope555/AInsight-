package com.ainsight.agent.core;

import com.ainsight.agent.llm.LlmClient;
import com.ainsight.agent.llm.dto.ChatCompletionResponse;
import com.ainsight.agent.llm.dto.ChatMessage;
import com.ainsight.agent.llm.dto.ToolCall;
import com.ainsight.agent.llm.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行引擎 —— 整个项目的心脏,自研 ReAct 循环:
 *
 *   while (轮数未超限) {
 *       让模型看 [历史 + 工具说明书];
 *       if (模型给出 tool_calls) { 执行工具 -> 结果以 role=tool 消息追加; continue; }
 *       else return 模型的文本回答;
 *   }
 *   超限 -> 收走工具,强制模型基于已有信息收尾。
 *
 * 阶段4为单轮任务版(无会话记忆);阶段6会接入 Redis 历史与 SSE 流式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

    /** 最大 LLM 调用轮数:防御模型抽风导致的死循环(每一轮都是真金白银的 token) */
    private static final int MAX_ITERATIONS = 8;

    /** 阶段4暂无真实会话,审计日志先用占位值;阶段6接入会话后传真实 id */
    private static final long PLACEHOLDER_CONVERSATION_ID = 0L;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public AgentResult execute(String question) {
        long start = System.currentTimeMillis();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt()));
        messages.add(ChatMessage.user(question));

        List<AgentStep> steps = new ArrayList<>();
        List<ToolDefinition> tools = toolRegistry.getDefinitions();
        int totalTokens = 0;

        for (int round = 1; round <= MAX_ITERATIONS; round++) {
            ChatCompletionResponse response = llmClient.chatWithTools(messages, tools);
            totalTokens += usageOf(response);

            ChatMessage assistant = response.getChoices().get(0).getMessage();
            if (assistant.getContent() == null) {
                assistant.setContent(""); // 部分厂商要求 content 非空,兜底
            }
            // 必须把 assistant 消息(含 tool_calls)原样追加,后续 tool 消息才有配对对象
            messages.add(assistant);

            List<ToolCall> toolCalls = assistant.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 模型不再调工具 => 这就是最终回答
                log.info("agent finished in {} round(s), {} tool call(s), totalTokens={}",
                        round, steps.size(), totalTokens);
                return new AgentResult(assistant.getContent(), steps, round,
                        totalTokens, System.currentTimeMillis() - start);
            }

            // 模型可能在一条消息里同时发起多个工具调用(并行调用),逐个执行并回填
            for (ToolCall call : toolCalls) {
                ToolExecutionResult exec = toolRegistry.execute(PLACEHOLDER_CONVERSATION_ID, call);
                steps.add(new AgentStep(round, call.getFunction().getName(),
                        call.getFunction().getArguments(),
                        truncate(exec.result(), 500), exec.costMs(), exec.success()));
                // role=tool 消息必须带 tool_call_id,和 assistant 消息里的调用一一配对
                messages.add(ChatMessage.tool(call.getId(), exec.result()));
            }
            log.info("agent round {}: executed {} tool call(s)", round, toolCalls.size());
        }

        // 超过最大轮数:收走工具(tools=null),强制模型基于已有信息给出总结性回答
        log.warn("agent hit MAX_ITERATIONS({}), forcing final answer", MAX_ITERATIONS);
        ChatCompletionResponse finalResponse = llmClient.chatWithTools(messages, null);
        totalTokens += usageOf(finalResponse);
        String answer = finalResponse.getChoices().get(0).getMessage().getContent();
        return new AgentResult(answer, steps, MAX_ITERATIONS + 1,
                totalTokens, System.currentTimeMillis() - start);
    }

    private String buildSystemPrompt() {
        return """
                你是 AInsight 平台的智能助手,可以调用工具获取真实数据来完成任务。
                规则:
                1. 涉及订单、天气等事实信息,必须使用工具查询,严禁编造;
                2. 工具返回错误或查不到时,如实告知用户,不要虚构结果;
                3. 一次任务可以按需调用多个工具,信息足够后直接给出最终回答;
                4. 回答要综合工具结果,用简洁、准确的中文。
                今天是 %s。""".formatted(LocalDate.now());
    }

    private int usageOf(ChatCompletionResponse response) {
        return response.getUsage() != null && response.getUsage().getTotalTokens() != null
                ? response.getUsage().getTotalTokens() : 0;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
