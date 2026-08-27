package com.zhixu.kb.note.service;

import java.util.List;

/**
 * 进程内本地 OCR 引擎（桌面版）：与远程 HTTP OCR 客户端同一产出契约。
 */
public interface LocalOcrEngine {

    /**
     * 对图片执行 OCR 识别。
     * @param imageBytes 图片二进制（png/jpg）
     * @return 按行拆分的识别文本（空行剔除）
     */
    List<String> recognize(byte[] imageBytes) throws Exception;
}
