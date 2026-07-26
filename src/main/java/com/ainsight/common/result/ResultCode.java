package com.ainsight.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务状态码统一定义。
 * 约定:0~999 与 HTTP 语义对齐的通用码;1xxx 用户;2xxx 会话;3xxx 知识库;4xxx Agent。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "success"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁,请稍后再试"),
    SYSTEM_ERROR(500, "系统繁忙,请稍后再试"),

    // ---- 1xxx 用户模块 ----
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已被注册"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),

    // ---- 2xxx 对话模块 ----
    CONVERSATION_NOT_FOUND(2001, "会话不存在"),

    // ---- 3xxx 知识库模块 ----
    DOCUMENT_NOT_FOUND(3001, "文档不存在"),
    DOCUMENT_PARSE_FAILED(3002, "文档解析失败"),
    UNSUPPORTED_FILE_TYPE(3003, "不支持的文件类型"),

    // ---- 4xxx Agent 模块 ----
    AGENT_EXECUTE_FAILED(4001, "AI 助手开小差了,请稍后重试"),
    LLM_CALL_FAILED(4002, "大模型服务调用失败");

    private final Integer code;
    private final String message;
}
