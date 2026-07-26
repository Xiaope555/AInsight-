package com.ainsight.demo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "参数校验演示请求体")
public class DemoRegisterRequest {

    @Schema(description = "用户名", example = "xiaopianzi")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在 3~20 之间")
    private String username;

    @Schema(description = "密码", example = "abc123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 之间")
    private String password;
}
