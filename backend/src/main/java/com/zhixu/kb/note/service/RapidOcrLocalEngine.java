package com.zhixu.kb.note.service;

import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RapidOCR 本地引擎（桌面版）：基于 PaddleOCR PP-OCR ONNX 模型 + 平台原生推理库，
 * 进程内运行，无需 Python/容器。模型与原生库由 rapidocr 依赖自带。
 * <p>
 * 引擎懒加载（首次调用才加载动态库与模型，避免拖慢应用启动）；
 * JNI 引擎非线程安全，调用串行化。
 */
@Slf4j
@Component
@Profile("desktop")
public class RapidOcrLocalEngine implements LocalOcrEngine {

    private final Object engineLock = new Object();
    private volatile InferenceEngine engine;

    private InferenceEngine engine() {
        InferenceEngine local = engine;
        if (local != null) {
            return local;
        }
        synchronized (engineLock) {
            if (engine == null) {
                long start = System.currentTimeMillis();
                engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
                log.info("RapidOCR local engine initialized: model={} costMs={}",
                        Model.ONNX_PPOCR_V4, System.currentTimeMillis() - start);
            }
            return engine;
        }
    }

    @Override
    public List<String> recognize(byte[] imageBytes) throws Exception {
        Path temp = Files.createTempFile("zhixu-ocr-", ".png");
        try {
            Files.write(temp, imageBytes);
            long start = System.currentTimeMillis();
            OcrResult result;
            synchronized (engineLock) {
                result = engine().runOcr(temp.toString());
            }
            List<String> lines = splitLines(result == null ? null : result.getStrRes());
            log.info("Local OCR done: lines={} costMs={}", lines.size(), System.currentTimeMillis() - start);
            return lines;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<String> splitLines(String strRes) {
        List<String> lines = new ArrayList<>();
        if (strRes == null || strRes.isBlank()) {
            return lines;
        }
        Arrays.stream(strRes.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(lines::add);
        return lines;
    }
}
