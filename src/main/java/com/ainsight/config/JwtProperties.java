package com.ainsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项,对应 application.yml 里的 ainsight.jwt.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "ainsight.jwt")
public class JwtProperties {

    /** HS256 签名密钥,长度必须 >= 32 字节,生产环境用环境变量注入 */
    private String secret;

    /** access token 有效期(分钟) */
    private long expireMinutes = 1440;
}
