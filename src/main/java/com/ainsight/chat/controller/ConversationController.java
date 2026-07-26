package com.ainsight.chat.controller;

import com.ainsight.chat.dto.ConversationResponse;
import com.ainsight.chat.dto.MessageResponse;
import com.ainsight.chat.entity.ChatConversation;
import com.ainsight.chat.service.ConversationService;
import com.ainsight.common.result.PageResult;
import com.ainsight.common.result.Result;
import com.ainsight.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "05-会话管理", description = "需要登录;只能操作自己的会话")
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "新建会话")
    @PostMapping
    public Result<ConversationResponse> create() {
        ChatConversation conversation = conversationService.create(SecurityUtils.getUserId());
        return Result.ok(new ConversationResponse(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt()));
    }

    @Operation(summary = "会话分页列表")
    @GetMapping
    public Result<PageResult<ConversationResponse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(conversationService.page(SecurityUtils.getUserId(), page, Math.min(size, 50)));
    }

    @Operation(summary = "会话历史消息", description = "从 MySQL 读全量文本消息(冷数据)")
    @GetMapping("/{id}/messages")
    public Result<List<MessageResponse>> messages(@PathVariable Long id) {
        return Result.ok(conversationService.messages(id, SecurityUtils.getUserId()));
    }

    @Operation(summary = "删除会话", description = "逻辑删除,同时清掉 Redis 热上下文")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.delete(id, SecurityUtils.getUserId());
        return Result.ok();
    }
}
