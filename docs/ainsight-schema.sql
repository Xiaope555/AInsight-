-- ============================================================
-- AInsight - Enterprise Knowledge Base & AI Agent Platform
-- Database schema v1.0  (MySQL 8.x)
-- 设计原则:
--   1. 不使用物理外键(阿里规范),用逻辑外键 + 索引
--   2. 全库 utf8mb4,兼容中文与 emoji
--   3. 所有表带 created_at;需要追踪变更的表带 updated_at
-- ============================================================

CREATE DATABASE IF NOT EXISTS ainsight
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ainsight;

-- ------------------------------------------------------------
-- 1. 用户表(阶段 2 使用)
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)  NOT NULL COMMENT '登录名',
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码,永不存明文',
    nickname   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER / ADMIN',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT ='用户表';

-- ------------------------------------------------------------
-- 2. 会话表(阶段 3/6 使用)
-- ------------------------------------------------------------
CREATE TABLE chat_conversation (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键=会话ID',
    user_id    BIGINT       NOT NULL COMMENT '逻辑外键 -> sys_user.id',
    title      VARCHAR(100) NOT NULL DEFAULT '新对话' COMMENT '会话标题(取首条提问前N字)',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='会话表';

-- ------------------------------------------------------------
-- 3. 消息表(阶段 3/4/6 使用)
--    完整保留 Agent 执行轨迹: user / assistant / tool / system 四种角色
-- ------------------------------------------------------------
CREATE TABLE chat_message (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT      NOT NULL COMMENT '逻辑外键 -> chat_conversation.id',
    role            VARCHAR(20) NOT NULL COMMENT 'system / user / assistant / tool',
    content         TEXT        DEFAULT NULL COMMENT '消息正文(assistant 发起工具调用时可为空)',
    tool_calls      JSON        DEFAULT NULL COMMENT 'assistant 发起的工具调用请求,原样存 LLM 返回的 JSON',
    tool_call_id    VARCHAR(64) DEFAULT NULL COMMENT 'role=tool 时,对应 assistant 消息里的调用 ID',
    token_usage     INT         DEFAULT NULL COMMENT '本条消息消耗 token 数(可选统计)',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv (conversation_id, id)
) ENGINE = InnoDB COMMENT ='消息表:对话历史全量持久化';

-- ------------------------------------------------------------
-- 4. 知识库文档表(阶段 5 使用)
-- ------------------------------------------------------------
CREATE TABLE kb_document (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '上传人,逻辑外键 -> sys_user.id',
    name        VARCHAR(200) NOT NULL COMMENT '文档名',
    file_type   VARCHAR(20)  NOT NULL COMMENT 'txt / md / pdf',
    file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '字节数',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING / READY / FAILED',
    chunk_count INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='知识库文档表';

-- ------------------------------------------------------------
-- 5. 文档切片表(阶段 5 使用)
--    embedding 用 JSON 存 float 数组;检索时加载进内存做余弦相似度
-- ------------------------------------------------------------
CREATE TABLE kb_chunk (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    document_id BIGINT   NOT NULL COMMENT '逻辑外键 -> kb_document.id',
    chunk_index INT      NOT NULL COMMENT '在原文档中的序号,用于按序还原上下文',
    content     TEXT     NOT NULL COMMENT '切片文本',
    embedding   JSON     DEFAULT NULL COMMENT '向量(float 数组),维度由 Embedding 模型决定',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_doc (document_id)
) ENGINE = InnoDB COMMENT ='文档切片表(含向量)';

-- ------------------------------------------------------------
-- 6. 订单表(阶段 4 使用,演示 Agent 查询业务数据的工具)
-- ------------------------------------------------------------
CREATE TABLE biz_order (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)   NOT NULL COMMENT '订单号',
    user_id      BIGINT        NOT NULL COMMENT '下单用户,逻辑外键 -> sys_user.id',
    product_name VARCHAR(100)  NOT NULL COMMENT '商品名',
    amount       DECIMAL(10,2) NOT NULL COMMENT '金额',
    status       VARCHAR(20)   NOT NULL COMMENT 'PAID / SHIPPED / COMPLETED / REFUNDED',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='订单表(Agent 工具演示用)';

-- ------------------------------------------------------------
-- 7. Agent 工具调用日志表(阶段 4 使用)
--    每次工具调用都留痕:排查问题 + 面试时讲"可观测性"的亮点
-- ------------------------------------------------------------
CREATE TABLE agent_tool_log (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT      NOT NULL COMMENT '逻辑外键 -> chat_conversation.id',
    tool_name       VARCHAR(64) NOT NULL COMMENT '工具名,如 query_order',
    arguments       JSON        DEFAULT NULL COMMENT 'LLM 生成的调用参数',
    result          TEXT        DEFAULT NULL COMMENT '工具执行结果(超长截断存储)',
    success         TINYINT     NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    cost_ms         INT         NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv (conversation_id)
) ENGINE = InnoDB COMMENT ='Agent 工具调用审计日志';

-- ------------------------------------------------------------
-- 种子数据:管理员账号 admin / admin123(密码为 BCrypt 哈希)
-- ------------------------------------------------------------
INSERT INTO sys_user (username, password, nickname, role, status) VALUES
('admin', '$2b$10$CWYHqIN0axesk.tH8VaiqOsEuTLj8gUS.lW4kCSdZQLAX/AFwc0i6', '管理员', 'ADMIN', 1);

-- ------------------------------------------------------------
-- 演示数据:订单(供阶段 4 的 OrderQueryTool 查询)
-- ------------------------------------------------------------
INSERT INTO biz_order (order_no, user_id, product_name, amount, status, created_at) VALUES
('20260726001', 1, '机械键盘 K870',        399.00, 'SHIPPED',   '2026-07-20 10:23:00'),
('20260726002', 1, '27寸 4K 显示器',      1899.00, 'PAID',      '2026-07-24 15:41:00'),
('20260726003', 1, 'Type-C 扩展坞',        219.00, 'COMPLETED', '2026-06-30 09:12:00'),
('20260726004', 2, '人体工学椅',          1299.00, 'REFUNDED',  '2026-07-01 20:05:00'),
('20260726005', 2, '降噪耳机 Pro',         899.00, 'SHIPPED',   '2026-07-25 11:30:00');
