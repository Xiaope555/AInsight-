package com.ainsight.agent.llm;

import com.ainsight.agent.llm.dto.ChatCompletionChunk;
import com.ainsight.agent.llm.dto.ChatCompletionRequest;
import com.ainsight.agent.llm.dto.ChatCompletionResponse;
import com.ainsight.agent.llm.dto.ChatMessage;
import com.ainsight.agent.llm.dto.EmbeddingRequest;
import com.ainsight.agent.llm.dto.EmbeddingResponse;
import com.ainsight.agent.llm.dto.ToolCall;
import com.ainsight.agent.llm.dto.ToolDefinition;
import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.ResultCode;
import com.ainsight.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 大模型客户端:唯一与 LLM HTTP API 打交道的类。
 * 上层(ChatService / AgentExecutor)只面向 Java 对象,协议细节全部封在这里。
 */
@Slf4j
@Component
public class LlmClient {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public LlmClient(LlmProperties properties, WebClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 响应缓冲默认仅 256KB,批量 embedding 响应会超,放大到 4MB
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /** 普通对话:不带工具,常规温度,非流式 */
    public ChatCompletionResponse chat(List<ChatMessage> messages) {
        return doChat(messages, null, properties.getTemperature());
    }

    /**
     * Agent 对话:携带工具说明书,低温度,非流式。
     * tools 传 null 时等价于普通对话 —— AgentExecutor 超轮数收尾时会用到。
     */
    public ChatCompletionResponse chatWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        return doChat(messages, tools, properties.getAgentTemperature());
    }

    /**
     * 流式对话(阶段6):stream=true,SSE 逐片消费。
     * 两个职责:
     * 1. delta.content 每来一片就回调 deltaConsumer(打字机效果的源头);
     * 2. 把"流式工具调用分片"重组成完整 ToolCall —— 同一调用的分片共享 index,
     *    function.arguments 是被切碎的 JSON 字符串,逐段拼接。
     * 返回:完整的 assistant 消息(纯文本回答,或带 toolCalls)。
     */
    public ChatMessage chatStream(List<ChatMessage> messages, List<ToolDefinition> tools,
                                  Consumer<String> deltaConsumer) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(properties.getChatModel())
                .messages(messages)
                .temperature(tools == null || tools.isEmpty()
                        ? properties.getTemperature() : properties.getAgentTemperature())
                .tools(tools == null || tools.isEmpty() ? null : tools)
                .stream(true)
                .build();
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCall> toolCallsByIndex = new TreeMap<>();
        long start = System.currentTimeMillis();
        try {
            Flux<String> events = webClient.post()
                    .uri("/chat/completions")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .map(errBody -> {
                                        log.error("llm stream http error: status={}, body={}",
                                                resp.statusCode(), errBody);
                                        return new BizException(ResultCode.LLM_CALL_FAILED);
                                    }))
                    .bodyToFlux(String.class)
                    // 注意:Flux.timeout 是"相邻两个事件的间隔"超时 —— 流卡住不能无限等
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

            // MVC 工作线程里用 toIterable 同步消费流,语义直白;break 会自动取消上游订阅
            for (String data : events.toIterable()) {
                if ("[DONE]".equals(data.trim())) {
                    break;
                }
                ChatCompletionChunk chunk = objectMapper.readValue(data, ChatCompletionChunk.class);
                if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                    continue;
                }
                ChatCompletionChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                if (delta == null) {
                    continue;
                }
                if (delta.getContent() != null && !delta.getContent().isEmpty()) {
                    content.append(delta.getContent());
                    deltaConsumer.accept(delta.getContent());
                }
                if (delta.getToolCalls() != null) {
                    mergeToolCallDeltas(toolCallsByIndex, delta.getToolCalls());
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("llm stream failed: {}", e.getMessage(), e);
            throw new BizException(ResultCode.LLM_CALL_FAILED);
        }
        log.info("llm stream done: contentLen={}, toolCalls={}, cost={}ms",
                content.length(), toolCallsByIndex.size(), System.currentTimeMillis() - start);
        ChatMessage assistant = ChatMessage.assistant(content.toString());
        if (!toolCallsByIndex.isEmpty()) {
            assistant.setToolCalls(new ArrayList<>(toolCallsByIndex.values()));
        }
        return assistant;
    }

    /** 把一批分片并入 "index -> 完整 ToolCall" 累积表 */
    private void mergeToolCallDeltas(Map<Integer, ToolCall> accumulator,
                                     List<ChatCompletionChunk.DeltaToolCall> deltas) {
        for (ChatCompletionChunk.DeltaToolCall d : deltas) {
            int index = d.getIndex() == null ? 0 : d.getIndex();
            ToolCall toolCall = accumulator.computeIfAbsent(index, k -> {
                ToolCall t = new ToolCall();
                t.setType("function");
                t.setFunction(new ToolCall.FunctionCall(null, ""));
                return t;
            });
            if (d.getId() != null) {
                toolCall.setId(d.getId());
            }
            if (d.getFunction() != null) {
                if (d.getFunction().getName() != null) {
                    toolCall.getFunction().setName(d.getFunction().getName());
                }
                if (d.getFunction().getArguments() != null) {
                    toolCall.getFunction().setArguments(
                            toolCall.getFunction().getArguments() + d.getFunction().getArguments());
                }
            }
        }
    }

    /** 批量向量化(阶段5 RAG 使用),单次条数受厂商限制,分批由调用方负责 */
    public List<float[]> embed(List<String> texts) {
        EmbeddingRequest request = new EmbeddingRequest(properties.getEmbeddingModel(), texts);
        long start = System.currentTimeMillis();
        EmbeddingResponse response = post("/embeddings", request, EmbeddingResponse.class);
        if (response == null || response.getData() == null || response.getData().size() != texts.size()) {
            log.error("embedding response size mismatch: expect {}, got {}",
                    texts.size(), response == null || response.getData() == null ? 0 : response.getData().size());
            throw new BizException(ResultCode.LLM_CALL_FAILED);
        }
        List<float[]> vectors = response.getData().stream()
                .sorted(Comparator.comparing(EmbeddingResponse.Item::getIndex))
                .map(item -> {
                    List<Float> e = item.getEmbedding();
                    float[] v = new float[e.size()];
                    for (int i = 0; i < e.size(); i++) {
                        v[i] = e.get(i);
                    }
                    return v;
                })
                .toList();
        log.info("embedding done: {} text(s), dim={}, cost={}ms",
                texts.size(), vectors.get(0).length, System.currentTimeMillis() - start);
        return vectors;
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

    /** 通用 POST + 同步等待(MVC 工作线程里 block 是正当用法,响应式链路里绝不可以) */
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
