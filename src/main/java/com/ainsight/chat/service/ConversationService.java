package com.ainsight.chat.service;

import com.ainsight.agent.llm.dto.ChatMessage;
import com.ainsight.chat.dto.ConversationResponse;
import com.ainsight.chat.dto.MessageResponse;
import com.ainsight.chat.entity.ChatConversation;
import com.ainsight.chat.entity.ChatMessageEntity;
import com.ainsight.chat.mapper.ChatMessageMapper;
import com.ainsight.chat.mapper.ConversationMapper;
import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.PageResult;
import com.ainsight.common.result.ResultCode;
import com.ainsight.config.ChatProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理 + 上下文冷热分离:
 *   热:Redis 存最近 N 条文本消息(带 TTL),每轮对话读写,低延迟;
 *   冷:MySQL 全量持久化,历史页展示与 Redis 未命中时的重建来源(Cache-Aside)。
 * 上下文只保留 user/assistant 的文本消息 —— 工具调用轨迹属于"单次任务的内部过程",
 * 不跨轮携带(省 token,也避免裁剪时切断 assistant/tool 消息配对导致协议非法)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    public static final String CONTEXT_KEY_PREFIX = "chat:ctx:";
    private static final String DEFAULT_TITLE = "新对话";

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatProperties chatProperties;

    public ChatConversation create(Long userId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setTitle(DEFAULT_TITLE);
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** 归属校验:查不到或不是自己的一律 2001 —— 不向外暴露"存在但无权"的差别信息 */
    public ChatConversation getOwned(Long conversationId, Long userId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BizException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    /** 分页列表(阶段2装好的分页插件在这里派上用场) */
    public PageResult<ConversationResponse> page(Long userId, int page, int size) {
        Page<ChatConversation> result = conversationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, userId)
                        .orderByDesc(ChatConversation::getId));
        List<ConversationResponse> records = result.getRecords().stream()
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(),
                        c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 历史消息(展示用):只取 user/assistant 的非空文本消息 */
    public List<MessageResponse> messages(Long conversationId, Long userId) {
        getOwned(conversationId, userId);
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .in(ChatMessageEntity::getRole, "user", "assistant")
                        .isNotNull(ChatMessageEntity::getContent)
                        .ne(ChatMessageEntity::getContent, "")
                        .orderByAsc(ChatMessageEntity::getId))
                .stream()
                .map(m -> new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    public void delete(Long conversationId, Long userId) {
        getOwned(conversationId, userId);
        conversationMapper.deleteById(conversationId); // @TableLogic 逻辑删
        stringRedisTemplate.delete(CONTEXT_KEY_PREFIX + conversationId);
    }

    /** 读上下文:Redis 命中直接用;未命中回源 MySQL 重建并回填(Cache-Aside 模式) */
    public List<ChatMessage> loadContext(Long conversationId) {
        String key = CONTEXT_KEY_PREFIX + conversationId;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<ChatMessage>>() {
                });
            }
        } catch (Exception e) {
            // Redis 故障降级:当无缓存处理,走 MySQL 重建 —— 缓存挂了功能不能挂
            log.warn("load context from redis failed: {}", e.getMessage());
        }
        List<ChatMessageEntity> rows = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getConversationId, conversationId)
                        .in(ChatMessageEntity::getRole, "user", "assistant")
                        .isNotNull(ChatMessageEntity::getContent)
                        .ne(ChatMessageEntity::getContent, "")
                        .orderByDesc(ChatMessageEntity::getId)
                        .last("LIMIT " + chatProperties.getContextMaxMessages()));
        List<ChatMessage> context = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) { // 倒序取的,反转回时间正序
            ChatMessageEntity row = rows.get(i);
            context.add("user".equals(row.getRole())
                    ? ChatMessage.user(row.getContent())
                    : ChatMessage.assistant(row.getContent()));
        }
        if (!context.isEmpty()) {
            writeContext(conversationId, context);
        }
        return context;
    }

    /**
     * 一轮对话完成:MySQL 追加两条消息 + Redis 上下文更新(基于本轮执行前的快照追加,
     * 避免"重建后再追加"造成重复)+ 首轮自动起标题。
     */
    public void saveTurn(ChatConversation conversation, String question, String answer,
                         Integer totalTokens, List<ChatMessage> previousContext) {
        ChatMessageEntity userRow = new ChatMessageEntity();
        userRow.setConversationId(conversation.getId());
        userRow.setRole("user");
        userRow.setContent(question);
        chatMessageMapper.insert(userRow);

        ChatMessageEntity assistantRow = new ChatMessageEntity();
        assistantRow.setConversationId(conversation.getId());
        assistantRow.setRole("assistant");
        assistantRow.setContent(answer);
        assistantRow.setTokenUsage(totalTokens);
        chatMessageMapper.insert(assistantRow);

        List<ChatMessage> context = new ArrayList<>(previousContext == null ? List.of() : previousContext);
        context.add(ChatMessage.user(question));
        context.add(ChatMessage.assistant(answer));
        int max = chatProperties.getContextMaxMessages();
        if (context.size() > max) {
            context = new ArrayList<>(context.subList(context.size() - max, context.size()));
        }
        writeContext(conversation.getId(), context);

        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            ChatConversation update = new ChatConversation();
            update.setId(conversation.getId());
            update.setTitle(question.length() > 20 ? question.substring(0, 20) : question);
            conversationMapper.updateById(update);
        }
    }

    private void writeContext(Long conversationId, List<ChatMessage> context) {
        try {
            stringRedisTemplate.opsForValue().set(
                    CONTEXT_KEY_PREFIX + conversationId,
                    objectMapper.writeValueAsString(context),
                    Duration.ofMinutes(chatProperties.getContextTtlMinutes()));
        } catch (Exception e) {
            log.warn("write context to redis failed: {}", e.getMessage()); // 降级:下次走 MySQL 重建
        }
    }
}
