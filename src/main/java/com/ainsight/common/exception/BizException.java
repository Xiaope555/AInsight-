package com.ainsight.common.exception;

import com.ainsight.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常:代码中主动抛出、语义明确的"预期内失败"。
 * 继承 RuntimeException:1) 调用方无需强制 try-catch;2) Spring 事务默认只对非受检异常回滚。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 同一个码,自定义更具体的提示文案 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
