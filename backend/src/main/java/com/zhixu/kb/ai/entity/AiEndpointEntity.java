package com.zhixu.kb.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台 AI 端点（多厂商 Key/模型，数据库管理，动态生效）。
 */
@Data
@TableName("ai_endpoints")
public class AiEndpointEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String baseUrl;
    private String apiKey;
    private String model;
    /** 向量化模型（可选）：配置后该端点可承担 embedding；为空时系统自动探测常见 embedding 模型 */
    private String embeddingModel;
    private Integer enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
