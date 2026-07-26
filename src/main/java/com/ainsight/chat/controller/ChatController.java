package com.ainsight.chat.controller;

import com.ainsight.chat.dto.AgentChatRequest;
import com.ainsight.chat.dto.AgentChatResponse;
import com.ainsight.chat.dto.SimpleChatRequest;
import com.ainsight.chat.dto.SimpleChatResponse;
import com.ainsight.chat.service.ChatService;
import com.ainsight.common.result.Result;
import com.ainsight.infra.ratelimit.RateLimit;
import com.ainsight.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "03-对话接口", description = "需要登录")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "单轮对话(阶段3)", description = "无记忆、无工具,链路自检用")
    @RateLimit(key = "simple-chat", windowSeconds = 60, maxCount = 20)
    @PostMapping("/simple")
    public Result<SimpleChatResponse> simpleChat(@Valid @RequestBody SimpleChatRequest request) {
        return Result.ok(chatService.simpleChat(request));
    }

    @Operation(summary = "Agent 对话(非流式)",
            description = "带会话记忆:conversationId 不传自动建会话;返回回答+工具轨迹+token")
    @RateLimit(key = "agent-chat", windowSeconds = 60, maxCount = 10)
    @PostMapping("/agent")
    public Result<AgentChatResponse> agentChat(@Valid @RequestBody AgentChatRequest request) {
        return Result.ok(chatService.agentChat(SecurityUtils.getUserId(), request));
    }

    @Operation(summary = "Agent 对话(SSE 流式)",
            description = "事件流:meta(会话id) -> tool_call/tool_result -> delta(打字机) -> done|error;"
                    + "用 curl -N 或前端 fetch+ReadableStream 消费")
    @RateLimit(key = "agent-chat", windowSeconds = 60, maxCount = 10)
    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentChatStream(@Valid @RequestBody AgentChatRequest request) {
        return chatService.agentChatStream(SecurityUtils.getUserId(), request);
    }
}
