package com.zhixu.kb.ai;

import java.util.regex.Pattern;

/**
 * AI 输出后处理：剥离思考链（&lt;think&gt;...&lt;/think&gt;）等模型推理内容，
 * 只保留最终回答，避免向用户展示内部推理过程。
 */
public final class AiTextCleaner {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private AiTextCleaner() {
    }

    /**
     * 非流式输出清洗：剥离 think 块。
     */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = THINK_BLOCK.matcher(text).replaceAll("").trim();
        return cleaned.length() == 0 ? text : cleaned;
    }

    /**
     * 流式输出清洗状态机：跨 chunk 剥离 &lt;think&gt;...&lt;/think&gt;。
     */
    public static class StreamCleaner {
        private boolean inThink = false;
        private final StringBuilder pending = new StringBuilder();

        public void feed(String chunk, java.util.function.Consumer<String> sink) {
            if (chunk == null || chunk.length() == 0) {
                return;
            }
            pending.append(chunk);
            String buf = pending.toString();

            if (!inThink) {
                int idx = buf.indexOf("<think>");
                if (idx >= 0) {
                    String before = buf.substring(0, idx);
                    if (before.length() > 0) {
                        sink.accept(before);
                    }
                    pending.setLength(0);
                    inThink = true;
                } else {
                    // 可能跨 chunk 的 <think> 前缀需要延迟判断
                    int keep = 0;
                    for (int i = Math.min(buf.length(), 7); i >= 1; i--) {
                        if (buf.length() >= i && "<think>".startsWith(buf.substring(buf.length() - i))) {
                            keep = i;
                            break;
                        }
                    }
                    int emitLen = buf.length() - keep;
                    if (emitLen > 0) {
                        sink.accept(buf.substring(0, emitLen));
                    }
                    pending.setLength(0);
                    if (keep > 0) {
                        pending.append(buf.substring(emitLen));
                    }
                }
            } else {
                int end = buf.indexOf("</think>");
                if (end >= 0) {
                    pending.setLength(0);
                    pending.append(buf.substring(end + 8));
                    inThink = false;
                    // 递归处理后续内容
                    if (pending.length() > 0) {
                        String rest = pending.toString();
                        pending.setLength(0);
                        feed(rest, sink);
                    }
                } else {
                    pending.setLength(0);
                }
            }
        }

        public void flush(java.util.function.Consumer<String> sink) {
            if (!inThink && pending.length() > 0) {
                sink.accept(pending.toString());
                pending.setLength(0);
            }
            pending.setLength(0);
            inThink = false;
        }
    }
}
