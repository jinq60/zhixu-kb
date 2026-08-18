package com.zhixu.kb.ask.model;

import lombok.Data;

/**
 * 问答导出载荷。
 */
@Data
public class AskExportPayload {
    private String fileName;
    private String content;
}
