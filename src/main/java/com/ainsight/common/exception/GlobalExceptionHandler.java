package com.ainsight.common.exception;

import com.ainsight.common.result.Result;
import com.ainsight.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器:Controller 层抛出的所有异常在这里被翻译成统一响应。
 * 注意:只拦截"进入了 Controller 调用链"的异常,Filter 里抛的异常到不了这里(阶段 2 会遇到并解决)。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常:预期内,warn 级别,不打堆栈(避免日志噪音) */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("[biz] code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 参数校验失败:拼出"哪个字段、错在哪"再返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[param] {}", detail);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), detail);
    }

    /** 请求体缺失或 JSON 格式非法 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[param] request body not readable");
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体缺失或 JSON 格式错误");
    }

    /** 访问不存在的路径:Spring Boot 3.2+ 抛 NoResourceFoundException,必须单独接住,否则会被兜底当成 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /** 兜底:预期外异常,error 级别 + 完整堆栈进日志;响应只给统一文案,绝不暴露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[system] unexpected error", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
