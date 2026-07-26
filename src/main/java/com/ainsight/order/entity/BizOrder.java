package com.ainsight.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体(演示业务表):Agent 工具查询的目标数据。
 * 在真实企业里,这就是"Agent 对接已有业务模块"的缩影。
 */
@Data
@TableName("biz_order")
public class BizOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private String productName;

    private BigDecimal amount;

    /** PAID / SHIPPED / COMPLETED / REFUNDED */
    private String status;

    private LocalDateTime createdAt;
}
