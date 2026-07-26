package com.ainsight.chat.controller;

import com.ainsight.agent.core.AgentResult;
import com.ainsight.chat.dto.AgentChatRequest;
import com.ainsight.chat.dto.SimpleChatRequest;
import com.ainsight.chat.dto.SimpleChatResponse;
import com.ainsight.chat.service.ChatService;
import com.ainsight.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03-对话接口", description = "需要登录")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "单轮对话(阶段3)", description = "无记忆、无工具,验证大模型链路是否打通")
    @PostMapping("/simple")
    public Result<SimpleChatResponse> simpleChat(@Valid @RequestBody SimpleChatRequest request) {
        return Result.ok(chatService.simpleChat(request));
    }

    @Operation(summary = "Agent 对话(阶段4)",
            description = "模型自主决定调用工具(查订单/查天气),返回最终回答 + 完整工具调用轨迹")
    @PostMapping("/agent")
    public Result<AgentResult> agentChat(@Valid @RequestBody AgentChatRequest request) {
        return Result.ok(chatService.agentChat(request));
    }
}
