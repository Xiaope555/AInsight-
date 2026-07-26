package com.ainsight.security;

import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 业务代码获取当前登录用户的唯一入口。
 * 原理:SecurityContextHolder 默认用 ThreadLocal 存放认证信息,同一次请求内任意位置可取。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BizException(ResultCode.UNAUTHORIZED);
    }

    public static Long getUserId() {
        return getLoginUser().id();
    }
}
