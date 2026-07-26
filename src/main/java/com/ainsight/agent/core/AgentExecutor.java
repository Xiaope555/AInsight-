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
 * Agent 执行引擎 —— 自研 ReAct 循环(阶段6起支持会话历史与流式)。
 *
 *   while (轮数未超限) {
 *       让模型看 [system + 历史 + 本轮问题 + 工具说明书];
 *       if (模型给出 tool_calls) { 执行工具 -> 结果以 role=tool 消息追加; continue; }
 *       else return 模型的文本回答;
 *   }
 *   超限 -> 收走工具,强制模型基于已有信息收尾。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

    /** 最大 LLM 调用轮数:防御模型抽风导致的死循环(每一轮都是真金白银的 token) */
    private static final int MAX_ITERATIONS = 8;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    /** 非流式执行(阶段6起携带会话历史与真实 conversationId) */
    public AgentResult execute(Long conversationId, List<ChatMessage> history, String question) {
        long start = System.currentTimeMillis();
        List<ChatMessage> messages = prepareMessages(history, question);
        List<AgentStep> steps = new ArrayList<>();
        List<ToolDefinition> tools = toolRegistry.getDefinitions();
        int totalTokens = 0;

        for (int round = 1; round <= MAX_ITERATIONS; round++) {
            ChatCompletionResponse response = llmClient.chatWithTools(messages, tools);
            totalTokens += usageOf(response);

            ChatMessage assistant = response.getChoices().get(0).getMessage();
            if (assistant.getContent() == null) {
                assistant.setContent("");
            }
            messages.add(assistant);

            List<ToolCall> toolCalls = assistant.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("agent finished in {} round(s), {} tool call(s), totalTokens={}",
                        round, steps.size(), totalTokens);
                return new AgentResult(assistant.getContent(), steps, round,
                        totalTokens, System.currentTimeMillis() - start);
            }
            runToolCalls(conversationId, toolCalls, steps, messages, round, null);
        }

        log.warn("agent hit MAX_ITERATIONS({}), forcing final answer", MAX_ITERATIONS);
        ChatCompletionResponse finalResponse = llmClient.chatWithTools(messages, null);
        totalTokens += usageOf(finalResponse);
        return new AgentResult(finalResponse.getChoices().get(0).getMessage().getContent(),
                steps, MAX_ITERATIONS + 1, totalTokens, System.currentTimeMillis() - start);
    }

    /**
     * 流式执行:回答增量与工具事件通过 listener 实时上报,方法本身阻塞到全部完成。
     * 注意:流式协议不返回 usage,totalTokens 记 0(如需统计可改用厂商的 include_usage 扩展)。
     */
    public AgentResult executeStream(Long conversationId, List<ChatMessage> history,
                                     String question, AgentStreamListener listener) {
        long start = System.currentTimeMillis();
        List<ChatMessage> messages = prepareMessages(history, question);
        List<AgentStep> steps = new ArrayList<>();
        List<ToolDefinition> tools = toolRegistry.getDefinitions();

        for (int round = 1; round <= MAX_ITERATIONS; round++) {
            // 每一轮都用流式请求:若这轮是工具决策,分片在 LlmClient 里被重组;
            // 若这轮是最终回答,delta 已经实时推给了客户端
            ChatMessage assistant = llmClient.chatStream(messages, tools, listener::onDelta);
            messages.add(assistant);

            List<ToolCall> toolCalls = assistant.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("agent(stream) finished in {} round(s), {} tool call(s)", round, steps.size());
                return new AgentResult(assistant.getContent(), steps, round,
                        0, System.currentTimeMillis() - start);
            }
            runToolCalls(conversationId, toolCalls, steps, messages, round, listener);
        }

        log.warn("agent(stream) hit MAX_ITERATIONS({}), forcing final answer", MAX_ITERATIONS);
        ChatMessage finalMessage = llmClient.chatStream(messages, null, listener::onDelta);
        return new AgentResult(finalMessage.getContent(), steps, MAX_ITERATIONS + 1,
                0, System.currentTimeMillis() - start);
    }

    private List<ChatMessage> prepareMessages(List<ChatMessage> history, String question) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt()));
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(question));
        return messages;
    }

    /** 执行一批工具调用(模型可能一条消息里并行发起多个),回填 role=tool 消息 */
    private void runToolCalls(Long conversationId, List<ToolCall> toolCalls, List<AgentStep> steps,
                              List<ChatMessage> messages, int round, AgentStreamListener listener) {
        for (ToolCall call : toolCalls) {
            if (listener != null) {
                listener.onToolCall(call.getFunction().getName(), call.getFunction().getArguments());
            }
            ToolExecutionResult exec = toolRegistry.execute(conversationId, call);
            AgentStep step = new AgentStep(round, call.getFunction().getName(),
                    call.getFunction().getArguments(),
                    truncate(exec.result(), 500), exec.costMs(), exec.success());
            steps.add(step);
            if (listener != null) {
                listener.onToolResult(step);
            }
            messages.add(ChatMessage.tool(call.getId(), exec.result()));
        }
        log.info("agent round {}: executed {} tool call(s)", round, toolCalls.size());
    }

    private String buildSystemPrompt() {
        return """
                你是 AInsight 平台的智能助手,可以调用工具获取真实数据来完成任务。
                规则:
                1. 涉及订单、天气、企业内部知识等事实信息,必须使用工具查询,严禁编造;
                2. 工具返回错误或查不到时,如实告知用户,不要虚构结果;
                3. 一次任务可以按需调用多个工具,信息足够后直接给出最终回答;
                4. 回答要综合工具结果,用简洁、准确的中文,引用知识库内容时注明来源文档。
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
