package com.zhixu.kb.ask.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 知识问答请求。
 */
@Data
public class AskRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过2000字符")
    private String question;
}
