package com.zhixu.kb.system.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户治理视图。
 */
@Data
public class AdminUserView {
    private Long id;
    private String username;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private List<String> roles;
}
