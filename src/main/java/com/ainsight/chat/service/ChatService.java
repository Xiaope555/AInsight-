package com.ainsight.chat.service;

import com.ainsight.agent.core.AgentExecutor;
import com.ainsight.agent.core.AgentResult;
import com.ainsight.agent.core.AgentStep;
import com.ainsight.agent.core.AgentStreamListener;
import com.ainsight.agent.llm.LlmClient;
import com.ainsight.agent.llm.dto.ChatCompletionResponse;
import com.ainsight.agent.llm.dto.ChatMessage;
import com.ainsight.chat.dto.AgentChatRequest;
import com.ainsight.chat.dto.AgentChatResponse;
import com.ainsight.chat.dto.SimpleChatRequest;
import com.ainsight.chat.dto.SimpleChatResponse;
import com.ainsight.chat.entity.ChatConversation;
import com.ainsight.config.ChatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
    private final ConversationService conversationService;
    private final ChatProperties chatProperties;
    private final ObjectMapper objectMapper;
    private final TaskExecutor applicationTaskExecutor;

    /** 阶段3:最简单的单轮对话(保留,用于链路自检) */
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

    /** 阶段6:非流式 Agent 对话,带会话记忆 */
    public AgentChatResponse agentChat(Long userId, AgentChatRequest request) {
        ChatConversation conversation = resolveConversation(userId, request.getConversationId());
        List<ChatMessage> history = conversationService.loadContext(conversation.getId());

        AgentResult result = agentExecutor.execute(conversation.getId(), history, request.getQuestion());

        conversationService.saveTurn(conversation, request.getQuestion(),
                result.answer(), result.totalTokens(), history);
        return new AgentChatResponse(conversation.getId(), result.answer(), result.steps(),
                result.llmRounds(), result.totalTokens(), result.costMs());
    }

    /**
     * 阶段6:SSE 流式 Agent 对话。
     * 事件协议:meta(会话id) -> [tool_call / tool_result ...] -> delta... -> done | error
     */
    public SseEmitter agentChatStream(Long userId, AgentChatRequest request) {
        // 会话解析与上下文读取必须留在 HTTP 线程:
        // SecurityContext 是 ThreadLocal,不会跟随下面的异步线程
        ChatConversation conversation = resolveConversation(userId, request.getConversationId());
        List<ChatMessage> history = conversationService.loadContext(conversation.getId());

        SseEmitter emitter = new SseEmitter(chatProperties.getSseTimeoutMinutes() * 60_000L);
        emitter.onTimeout(emitter::complete);

        applicationTaskExecutor.execute(() -> {
            try {
                sendEvent(emitter, "meta", Map.of("conversationId", conversation.getId()));

                AgentResult result = agentExecutor.executeStream(
                        conversation.getId(), history, request.getQuestion(),
                        new AgentStreamListener() {
                            @Override
                            public void onToolCall(String toolName, String arguments) {
                                sendEvent(emitter, "tool_call", Map.of(
                                        "tool", toolName,
                                        "arguments", arguments == null ? "" : arguments));
                            }

                            @Override
                            public void onToolResult(AgentStep step) {
                                sendEvent(emitter, "tool_result", Map.of(
                                        "tool", step.toolName(),
                                        "success", step.success(),
                                        "costMs", step.costMs()));
                            }

                            @Override
                            public void onDelta(String delta) {
                                sendEvent(emitter, "delta", Map.of("text", delta));
                            }
                        });

                conversationService.saveTurn(conversation, request.getQuestion(),
                        result.answer(), null, history);
                sendEvent(emitter, "done", Map.of(
                        "llmRounds", result.llmRounds(),
                        "toolCalls", result.steps().size(),
                        "costMs", result.costMs()));
                emitter.complete();
            } catch (Exception e) {
                log.error("agent stream failed", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "AI 助手开小差了,请稍后重试"));
                } catch (Exception ignored) {
                    // 客户端已断开,无需再送
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    private ChatConversation resolveConversation(Long userId, Long conversationId) {
        return conversationId == null
                ? conversationService.create(userId)
                : conversationService.getOwned(conversationId, userId);
    }

    /** SSE 发送:客户端断开会抛 IOException,转成运行时异常让执行循环尽快终止(停止烧 token) */
    private void sendEvent(SseEmitter emitter, String name, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new RuntimeException("sse client disconnected", e);
        }
    }
}
