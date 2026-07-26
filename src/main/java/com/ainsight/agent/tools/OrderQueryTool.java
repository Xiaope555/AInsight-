package com.ainsight.agent.tools;

import com.ainsight.agent.core.AgentTool;
import com.ainsight.agent.core.ToolParam;
import com.ainsight.order.entity.BizOrder;
import com.ainsight.order.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具一:查询数据库中的订单。
 * 这是"Agent 调用后端数据库"的标准范式:模型给参数 -> Java 查库 -> 结构化结果喂回。
 */
@Component
@RequiredArgsConstructor
public class OrderQueryTool implements AgentTool {

    private static final Map<String, String> STATUS_DESC = Map.of(
            "PAID", "已支付,待发货",
            "SHIPPED", "已发货,运输中",
            "COMPLETED", "已完成",
            "REFUNDED", "已退款");

    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Data
    public static class Params {
        @ToolParam(description = "订单号,一串数字,例如 20260726001")
        private String orderNo;
    }

    @Override
    public String name() {
        return "query_order";
    }

    @Override
    public String description() {
        return "根据订单号查询订单详情,包括商品名、金额、订单状态、下单时间。当用户询问订单相关信息时使用。";
    }

    @Override
    public Class<?> parameterType() {
        return Params.class;
    }

    @Override
    public String execute(Object args) throws Exception {
        Params params = (Params) args;
        BizOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<BizOrder>().eq(BizOrder::getOrderNo, params.getOrderNo()));
        if (order == null) {
            return "未找到订单号为 " + params.getOrderNo() + " 的订单,请确认订单号是否正确。";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("productName", order.getProductName());
        result.put("amount", order.getAmount());
        result.put("status", order.getStatus());
        result.put("statusDesc", STATUS_DESC.getOrDefault(order.getStatus(), order.getStatus()));
        result.put("createdAt", String.valueOf(order.getCreatedAt()));
        return objectMapper.writeValueAsString(result);
    }
}
