package com.ainsight.infra.ratelimit;

import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.ResultCode;
import com.ainsight.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

/**
 * @RateLimit 的实现:Redis ZSET 滑动窗口,Lua 脚本保证原子性。
 *
 * 为什么滑动窗口:固定窗口(INCR+EXPIRE)有边界突刺问题 ——
 *   两个相邻窗口的临界处可以挤进 2 倍请求;ZSET 按时间戳记录每次请求,
 *   窗口随当前时刻平滑滑动,不存在边界。
 * 为什么 Lua:清理旧记录 -> 计数 -> 判断 -> 写入 这四步必须原子,
 *   否则并发下"先检查后写入"存在竞态,限流形同虚设。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String LUA = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])
            local count = redis.call('ZCARD', KEYS[1])
            if count >= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String bizKey = rateLimit.key().isEmpty()
                ? joinPoint.getSignature().getName() : rateLimit.key();
        String redisKey = "rate:" + bizKey + ":" + dimension(rateLimit.target());
        long windowMillis = rateLimit.windowSeconds() * 1000L;

        Long allowed = stringRedisTemplate.execute(SCRIPT, List.of(redisKey),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowMillis),
                String.valueOf(rateLimit.maxCount()),
                UUID.randomUUID().toString());

        if (allowed == null || allowed == 0) {
            log.warn("rate limited: {}", redisKey);
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }
        return joinPoint.proceed();
    }

    private String dimension(LimitTarget target) {
        return switch (target) {
            case USER -> "u" + SecurityUtils.getUserId();
            case IP -> "ip" + clientIp();
            case GLOBAL -> "global";
        };
    }

    private String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // 经过代理时取最左侧的真实客户端 IP
        }
        return request.getRemoteAddr();
    }
}
