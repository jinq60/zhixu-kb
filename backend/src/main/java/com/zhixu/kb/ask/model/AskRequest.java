package com.zhixu.kb.ask.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识问答请求。
 */
@Data
public class AskRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过2000字符")
    private String question;

    /** 会话 ID：同一次多轮对话共享；为空时服务端创建新会话 */
    @Size(max = 36, message = "会话ID格式不正确")
    private String conversationId;
}
