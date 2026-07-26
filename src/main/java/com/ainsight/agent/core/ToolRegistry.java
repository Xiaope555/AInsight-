package com.ainsight.agent.core;

import com.ainsight.agent.entity.AgentToolLog;
import com.ainsight.agent.llm.dto.ToolCall;
import com.ainsight.agent.llm.dto.ToolDefinition;
import com.ainsight.agent.mapper.ToolLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心:
 * 1. 启动时收集容器里所有 AgentTool Bean,反射生成每个工具的 JSON Schema(工具说明书);
 * 2. 运行时按模型指令执行工具,任何异常都转成错误文本(喂回模型让它自己应对,而不是让整个请求 500);
 * 3. 每次执行落一条 agent_tool_log 审计日志。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final List<ToolDefinition> definitions = new ArrayList<>();
    private final ObjectMapper objectMapper;
    private final ToolLogMapper toolLogMapper;

    public ToolRegistry(List<AgentTool> toolBeans, ObjectMapper objectMapper, ToolLogMapper toolLogMapper) {
        this.objectMapper = objectMapper;
        this.toolLogMapper = toolLogMapper;
        for (AgentTool tool : toolBeans) {
            tools.put(tool.name(), tool);
            definitions.add(ToolDefinition.of(tool.name(), tool.description(),
                    buildSchema(tool.parameterType())));
            log.info("agent tool registered: {} - {}", tool.name(), tool.description());
        }
    }

    /** 工具说明书列表(每轮请求都随 messages 一起发给模型) */
    public List<ToolDefinition> getDefinitions() {
        return definitions;
    }

    /**
     * 执行一次模型发起的工具调用。
     * 设计要点:这里【不抛异常】。工具失败的信息也是有价值的"观察结果",
     * 喂回模型后它会换个思路或如实告知用户 —— Agent 的容错来自于此。
     */
    public ToolExecutionResult execute(Long conversationId, ToolCall call) {
        String toolName = call.getFunction().getName();
        String argsJson = call.getFunction().getArguments();
        long start = System.currentTimeMillis();
        boolean success = true;
        String result;
        try {
            AgentTool tool = tools.get(toolName);
            if (tool == null) {
                success = false;
                result = "错误:不存在名为 " + toolName + " 的工具";
            } else {
                // 协议细节:arguments 是 JSON 字符串,必须先反序列化成参数对象
                String json = (argsJson == null || argsJson.isBlank()) ? "{}" : argsJson;
                Object args = objectMapper.readValue(json, tool.parameterType());
                result = tool.execute(args);
            }
        } catch (Exception e) {
            success = false;
            log.warn("tool [{}] failed: {}", toolName, e.getMessage());
            result = "工具执行失败:" + e.getMessage();
        }
        long cost = System.currentTimeMillis() - start;
        log.info("tool [{}] executed, success={}, cost={}ms, args={}", toolName, success, cost, argsJson);
        saveLog(conversationId, toolName, argsJson, result, success, cost);
        return new ToolExecutionResult(result, success, cost);
    }

    /** 审计日志失败绝不能影响主流程,单独兜异常 */
    private void saveLog(Long conversationId, String toolName, String argsJson,
                         String result, boolean success, long costMs) {
        try {
            AgentToolLog logRow = new AgentToolLog();
            logRow.setConversationId(conversationId);
            logRow.setToolName(toolName);
            logRow.setArguments(argsJson);
            logRow.setResult(result != null && result.length() > 2000
                    ? result.substring(0, 2000) + "...(truncated)" : result);
            logRow.setSuccess(success ? 1 : 0);
            logRow.setCostMs((int) costMs);
            toolLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("save tool log failed: {}", e.getMessage());
        }
    }

    /**
     * 反射生成 JSON Schema:
     * {"type":"object","properties":{"orderNo":{"type":"string","description":"..."}},"required":["orderNo"]}
     */
    private Map<String, Object> buildSchema(Class<?> parameterType) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Field field : parameterType.getDeclaredFields()) {
            ToolParam anno = field.getAnnotation(ToolParam.class);
            if (anno == null) {
                continue; // 未标注的字段不进说明书
            }
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType(field.getType()));
            prop.put("description", anno.description());
            properties.put(field.getName(), prop);
            if (anno.required()) {
                required.add(field.getName());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    /** Java 类型 -> JSON Schema 类型 */
    private String jsonType(Class<?> type) {
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "integer";
        }
        if (type == Double.class || type == double.class || type == Float.class
                || type == float.class || type == BigDecimal.class) {
            return "number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        return "string";
    }
}
