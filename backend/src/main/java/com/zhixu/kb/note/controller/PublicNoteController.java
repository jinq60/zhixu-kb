package com.zhixu.kb.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.note.model.PublicNoteDetailResponse;
import com.zhixu.kb.note.model.PublicNoteSummary;
import com.zhixu.kb.note.model.NoteStructureResponse;
import com.zhixu.kb.note.service.PublicNoteService;
import com.zhixu.kb.note.service.NoteStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/notes")
@RequiredArgsConstructor
public class PublicNoteController {

    private final PublicNoteService publicNoteService;
    private final NoteStructureService noteStructureService;

    @GetMapping
    public Result<Page<PublicNoteSummary>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(publicNoteService.listPublished(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<PublicNoteDetailResponse> detail(@PathVariable Long id) {
        return Result.success(publicNoteService.detail(id));
    }

    @GetMapping("/{id}/structure")
    public Result<NoteStructureResponse> structure(@PathVariable Long id) {
        return Result.success(noteStructureService.getReadableStructure(id));
    }
}
