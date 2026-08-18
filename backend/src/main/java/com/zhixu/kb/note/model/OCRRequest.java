package com.zhixu.kb.note.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OCRRequest {
    private String engine;
    private List<Long> fileIds = new ArrayList<>();
}
