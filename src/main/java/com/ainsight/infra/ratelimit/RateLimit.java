package com.ainsight.infra.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式限流:标在 Controller 方法上即生效(AOP + Redis Lua 滑动窗口)。
 * 示例:@RateLimit(key = "agent-chat", windowSeconds = 60, maxCount = 10)
 *       => 每个用户每 60 秒最多 10 次,超出返回业务码 429。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 业务标识(Redis key 的一部分),默认取方法名 */
    String key() default "";

    /** 窗口长度(秒) */
    int windowSeconds() default 60;

    /** 窗口内最大请求数 */
    int maxCount() default 10;

    /** 限流维度:登录接口这类匿名场景用 IP,其余默认按用户 */
    LimitTarget target() default LimitTarget.USER;
}
