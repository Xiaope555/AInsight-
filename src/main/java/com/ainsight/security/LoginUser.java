package com.ainsight.security;

/**
 * 放进 SecurityContext 的"当前登录用户",数据全部来自 JWT claims,认证过程零数据库查询。
 * Java 17 record:不可变、自动生成构造器/访问器,天然适合这种纯数据载体。
 */
public record LoginUser(Long id, String username, String role) {
}
