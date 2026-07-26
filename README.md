# AInsight — 企业智能知识库与 AI Agent 助手平台

> 基于 Spring Boot 3 的后端系统:用户用自然语言提问,AI Agent 自主决定调用后端工具
> (查数据库、检索知识库、调外部 API),多轮完成任务并返回答案。
>
> ⚠️ 项目开发中,完整文档与部署说明将在收尾阶段补全。

## 技术栈

Java 17 · Spring Boot 3.5 · Spring Security + JWT · MyBatis-Plus · MySQL 8 · Redis 7
· WebClient · Knife4j · 通义千问(OpenAI 兼容协议,可无缝切换任意厂商)

## 当前进度

- [x] 阶段0:整体架构设计(7 张表、模块划分、架构图见 `docs/`)
- [x] 阶段1:工程骨架 + 统一响应 + 全局异常 + Knife4j + Docker Compose 环境
- [x] 阶段2:用户模块 + Spring Security + JWT 无状态认证 + Redis 登出黑名单
- [x] 阶段3:接入大模型(OpenAI 兼容协议,WebClient 封装)
- [x] 阶段4:**自研 Agent 执行引擎** —— 注解+反射自动注册工具与生成 JSON Schema,
      Function Calling ReAct 多轮循环,工具调用审计日志(查订单 / 查天气)
- [ ] 阶段5:知识库 RAG(文档切分 → 向量化 → 余弦 Top-K 语义检索,接入 Agent 工具)
- [ ] 阶段6:会话历史(Redis)+ SSE 流式输出 + Redis 限流
- [ ] 阶段7:完整 README、接口文档、部署说明

## 快速启动

```bash
# 1. 启动 MySQL + Redis(首次自动建表并导入种子数据)
docker compose up -d

# 2. 配置大模型 API Key(阿里云百炼,或任意 OpenAI 兼容服务)
export AINSIGHT_LLM_API_KEY=sk-your-key

# 3. 启动应用
mvn spring-boot:run

# 4. 打开接口文档调试
# http://localhost:8080/doc.html   (种子账号 admin / admin123)
```

## 效果示例

```
POST /api/chat/agent
{"question": "帮我查一下订单 20260726001 的状态,再告诉我北京今天的天气"}

→ Agent 自主调用 query_order(MySQL)与 get_weather(外部API),
  综合两个工具结果给出回答,并返回完整调用轨迹(steps)与 token 消耗。
```
