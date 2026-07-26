package com.ainsight.agent.core;

/**
 * Agent 工具的统一抽象。实现类加 @Component 即完成注册:
 * Spring 会把所有 AgentTool Bean 注入 ToolRegistry(容器即插件机制)。
 *
 * 写好一个工具的三条军规:
 * 1. 原子性:一个工具只干一件事;
 * 2. description 是给模型看的 API 文档,写清楚"什么场景该用我";
 * 3. 返回结构化结果(JSON 字符串),模型好理解、好引用。
 */
public interface AgentTool {

    /** 工具名:小写下划线风格,模型按这个名字发起调用 */
    String name();

    /** 工具用途描述(决定模型会不会、什么时候调用你) */
    String description();

    /** 参数类:字段用 @ToolParam 标注,Schema 由反射自动生成 */
    Class<?> parameterType();

    /**
     * 执行工具。
     * @param args 已经反序列化好的 parameterType 实例
     * @return 给模型看的结果(建议 JSON 字符串);查不到也返回可读的说明文字
     * @throws Exception 任何异常都会被 ToolRegistry 捕获并转成错误文本喂回模型
     */
    String execute(Object args) throws Exception;
}
