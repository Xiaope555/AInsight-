package com.ainsight.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档基础信息。springdoc 扫描 Controller 生成 OpenAPI 3 规范(/v3/api-docs),
 * knife4j 负责把它渲染成好用的调试页面(/doc.html)。
 * 阶段 2 起声明了 Bearer 认证方案:doc.html 右上角 Authorize 里粘贴 token,
 * 之后页面里所有调试请求都会自动带上 Authorization 头。
 */
@Configuration
public class Knife4jConfig {

    private static final String SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI ainsightOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AInsight API")
                        .description("企业智能知识库与 AI Agent 助手平台 - 接口文档")
                        .version("v1.0.0")
                        .contact(new Contact().name("xiaopianzi")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
