package com.zhixu.kb.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户级 AI 配置（多厂商 API Key，加密存储）。
 * provider: deepseek / openai / moonshot / zhipu / qwen / siliconflow / custom
 */
@Data
@TableName("ai_user_config")
public class AiUserConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
