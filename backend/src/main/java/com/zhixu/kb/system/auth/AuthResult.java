package com.zhixu.kb.system.auth;

import com.zhixu.kb.system.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证适配器返回的结果。
 */
@Data
@AllArgsConstructor
public class AuthResult {

    private SysUser user;

    /** 是否为本次请求新建的用户 */
    private boolean newlyCreated;
}
