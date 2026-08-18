package com.zhixu.kb.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户多种认证方式绑定关系。
 */
@Data
@TableName("sys_user_auth")
public class SysUserAuth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 认证方式：password / email_code / sms_code / google / github / qq */
    private String provider;

    /** 第三方账号标识、邮箱、手机号等 */
    private String account;

    /** 预留凭据字段 */
    private String credential;

    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
