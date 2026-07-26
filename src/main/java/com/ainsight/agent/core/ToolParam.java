package com.ainsight.agent.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在工具参数类的字段上,ToolRegistry 启动时用反射读取它,自动生成 JSON Schema。
 * 好处:参数定义和说明书永远同步,新增工具零配置。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ToolParam {

    /** 参数含义描述 —— 写给模型看的,越具体模型填得越准 */
    String description();

    boolean required() default true;
}
