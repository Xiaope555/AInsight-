package com.ainsight.demo;

import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.Result;
import com.ainsight.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示接口:专门用来验证统一响应 + 全局异常 + 参数校验 + 接口文档。
 * 阶段 2 完成后可以删除。
 */
@Tag(name = "00-演示接口", description = "验证统一响应与全局异常,阶段 2 后可删除")
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @Operation(summary = "连通性测试", description = "验证统一响应结构 Result<T>")
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }

    @Operation(summary = "业务异常演示", description = "验证 BizException 被全局异常处理器接住")
    @GetMapping("/biz-error")
    public Result<Void> bizError() {
        throw new BizException(ResultCode.USER_NOT_FOUND);
    }

    @Operation(summary = "系统异常演示", description = "验证预期外异常被兜底,客户端看不到堆栈")
    @GetMapping("/system-error")
    public Result<Integer> systemError() {
        int boom = 1 / 0; // deliberately blow up
        return Result.ok(boom);
    }

    @Operation(summary = "参数校验演示", description = "验证 @Valid 校验失败被统一处理")
    @PostMapping("/validate")
    public Result<DemoRegisterRequest> validate(@Valid @RequestBody DemoRegisterRequest request) {
        return Result.ok(request);
    }
}
