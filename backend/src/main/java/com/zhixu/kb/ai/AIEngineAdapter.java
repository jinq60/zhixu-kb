package com.zhixu.kb.ai;

import java.util.Map;
import java.util.function.Consumer;

public interface AIEngineAdapter {
    String generateResponse(String prompt, Map<String, Object> parameters);

    default void generateStreamResponse(String prompt,
                                        Map<String, Object> parameters,
                                        Consumer<String> chunkConsumer) {
        if (chunkConsumer == null) {
            return;
        }
        String full = generateResponse(prompt, parameters);
        if (full == null || full.length() == 0) {
            return;
        }
        int chunkSize = 24;
        for (int i = 0; i < full.length(); i += chunkSize) {
            chunkConsumer.accept(full.substring(i, Math.min(i + chunkSize, full.length())));
        }
    }

    boolean isHealthy();
}