package com.linman.account.security;

import com.linman.account.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文取出当前登录用户 id。
 * 鉴权过滤器已把 principal 设为 CustomUserDetails。
 */
public class SecurityHelper {
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return ((CustomUserDetails) auth.getPrincipal()).getUserId();
    }
}
