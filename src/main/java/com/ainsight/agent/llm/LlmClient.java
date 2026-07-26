package com.ainsight.agent.llm;

import com.ainsight.agent.llm.dto.ChatCompletionRequest;
import com.ainsight.agent.llm.dto.ChatCompletionResponse;
import com.ainsight.agent.llm.dto.ChatMessage;
import com.ainsight.agent.llm.dto.ToolDefinition;
import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.ResultCode;
import com.ainsight.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * 大模型客户端:唯一与 LLM HTTP API 打交道的类。
 * 上层(ChatService / AgentExecutor)只面向 Java 对象,协议细节全部封在这里。
 */
@Slf4j
@Component
public class LlmClient {

    private final LlmProperties properties;
    private final WebClient webClient;

    public LlmClient(LlmProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 响应缓冲默认仅 256KB,阶段5批量 embedding 响应会超,提前放大到 4MB
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /** 普通对话:不带工具,常规温度(流式版本阶段6实现) */
    public ChatCompletionResponse chat(List<ChatMessage> messages) {
        return doChat(messages, null, properties.getTemperature());
    }

    /**
     * Agent 对话:携带工具说明书,低温度。
     * 温度调低的原因:工具调用要求模型"严谨地填参数",而不是"有创造力地发挥"。
     * tools 传 null 时等价于普通对话 —— AgentExecutor 超轮数收尾时会用到。
     */
    public ChatCompletionResponse chatWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        return doChat(messages, tools, properties.getAgentTemperature());
    }

    private ChatCompletionResponse doChat(List<ChatMessage> messages,
                                          List<ToolDefinition> tools, Double temperature) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(properties.getChatModel())
                .messages(messages)
                .temperature(temperature)
                .tools(tools == null || tools.isEmpty() ? null : tools)
                .stream(false)
                .build();
        long start = System.currentTimeMillis();
        ChatCompletionResponse response = post("/chat/completions", request, ChatCompletionResponse.class);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.error("llm returned empty choices");
            throw new BizException(ResultCode.LLM_CALL_FAILED);
        }
        if (response.getUsage() != null) {
            log.info("llm chat done: model={}, cost={}ms, promptTokens={}, completionTokens={}, finishReason={}",
                    response.getModel(), System.currentTimeMillis() - start,
                    response.getUsage().getPromptTokens(), response.getUsage().getCompletionTokens(),
                    response.getChoices().get(0).getFinishReason());
        }
        return response;
    }

    /**
     * 通用 POST + 同步等待。
     * 在 MVC 的工作线程里 block() 是正当用法(线程本来就是同步模型);
     * 若未来切响应式技术栈,绝不能在响应式链路里 block —— 会死锁。
     */
    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            return webClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .map(errBody -> {
                                        log.error("llm http error: status={}, body={}",
                                                resp.statusCode(), errBody);
                                        return new BizException(ResultCode.LLM_CALL_FAILED);
                                    }))
                    .bodyToMono(responseType)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("llm call failed: {}", e.getMessage(), e);
            throw new BizException(ResultCode.LLM_CALL_FAILED);
        }
    }
}
