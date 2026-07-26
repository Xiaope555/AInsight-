package com.ainsight.chat.service;

import com.ainsight.agent.core.AgentExecutor;
import com.ainsight.agent.core.AgentResult;
import com.ainsight.agent.llm.LlmClient;
import com.ainsight.agent.llm.dto.ChatCompletionResponse;
import com.ainsight.agent.llm.dto.ChatMessage;
import com.ainsight.chat.dto.AgentChatRequest;
import com.ainsight.chat.dto.SimpleChatRequest;
import com.ainsight.chat.dto.SimpleChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** system prompt:模型的"人设与规则",永远放在 messages 第一条 */
    private static final String SYSTEM_PROMPT = """
            你是 AInsight 平台的智能助手,请用简洁、准确的中文回答用户问题。
            不确定的内容要明确说明不确定,不要编造。""";

    private final LlmClient llmClient;
    private final AgentExecutor agentExecutor;

    /**
     * 阶段4:Agent 对话 —— 模型自主决定调用哪些工具、调几轮。
     * 目前是薄封装;阶段6会在这里接入会话历史的读取与持久化。
     */
    public AgentResult agentChat(AgentChatRequest request) {
        return agentExecutor.execute(request.getQuestion());
    }

    /**
     * 阶段3:最简单的单轮对话 —— 无记忆、无工具,验证 LLM 链路。
     * (多轮记忆在阶段6,工具调用在阶段4)
     */
    public SimpleChatResponse simpleChat(SimpleChatRequest request) {
        long start = System.currentTimeMillis();
        List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(request.getMessage()));

        ChatCompletionResponse response = llmClient.chat(messages);

        String reply = response.getChoices().get(0).getMessage().getContent();
        ChatCompletionResponse.Usage usage = response.getUsage();
        return new SimpleChatResponse(
                reply,
                response.getModel(),
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                usage != null ? usage.getTotalTokens() : null,
                System.currentTimeMillis() - start);
    }
}
