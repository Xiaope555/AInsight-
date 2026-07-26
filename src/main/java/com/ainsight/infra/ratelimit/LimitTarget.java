package com.ainsight.infra.ratelimit;

/** 限流维度:按登录用户 / 按客户端 IP / 全局共享一个额度 */
public enum LimitTarget {
    USER,
    IP,
    GLOBAL
}
