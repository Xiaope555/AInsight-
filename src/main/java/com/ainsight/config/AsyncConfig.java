package com.ainsight.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 开启 @Async 异步方法支持。
 * Spring Boot 已自动配置好线程池(applicationTaskExecutor),@Async 默认使用它;
 * 池参数可在 yml 用 spring.task.execution.* 调整,这里只需要打开开关。
 *
 * 用途:文档上传后的"切分+向量化"可能耗时数秒到数十秒,
 * 不能让 HTTP 请求干等 —— 上传立即返回,后台异步处理,前端轮询状态。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
