package com.zhixu.kb.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_binding")
public class DeviceBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String deviceId;
    private String deviceName;
    private LocalDateTime lastSeenAt;
    private Integer revoked;
    private LocalDateTime createTime;
}
