package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.CategoryMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.model.PublicNoteDetailResponse;
import com.zhixu.kb.note.model.PublicNoteSummary;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicNoteService {

    private final NoteMapper noteMapper;
    private final CategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;

    public Page<PublicNoteSummary> listPublished(int page, int size, String keyword) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getIsDeleted, 0)
                .eq(Note::getStatus, 1)
                .orderByDesc(Note::getUpdateTime);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Note::getTitle, keyword)
                    .or()
                    .like(Note::getContent, keyword)
                    .or()
                    .like(Note::getOcrText, keyword)
                    .or()
                    .like(Note::getSummary, keyword)
                    .or()
                    .like(Note::getKeywords, keyword));
        }

        Page<Note> rawPage = noteMapper.selectPage(new Page<>(page, size), wrapper);
        Page<PublicNoteSummary> resultPage = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        resultPage.setRecords(toSummaryList(rawPage.getRecords()));
        return resultPage;
    }

    public PublicNoteDetailResponse detail(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }

        Long currentUserId = SecurityUtils.getUserId();
        boolean editable = currentUserId != null && currentUserId.equals(note.getUserId());
        boolean published = note.getStatus() != null && note.getStatus() == 1;
        if (!editable && !published) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在或未发布");
        }

        Category category = note.getCategoryId() == null ? null : categoryMapper.selectById(note.getCategoryId());
        SysUser author = sysUserMapper.selectById(note.getUserId());
        return new PublicNoteDetailResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getOcrText(),
                note.getSummary(),
                note.getKeywords(),
                note.getCoverImage(),
                note.getStatus(),
                category == null ? null : category.getName(),
                author == null ? "未知用户" : author.getUsername(),
                note.getUserId(),
                editable,
                published,
                note.getCreateTime(),
                note.getUpdateTime()
        );
    }

    private List<PublicNoteSummary> toSummaryList(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> categoryIds = notes.stream()
                .map(Note::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> authorIds = notes.stream()
                .map(Note::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> categoryNameMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> authorNameMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));

        return notes.stream()
                .map(note -> new PublicNoteSummary(
                        note.getId(),
                        note.getTitle(),
                        note.getSummary(),
                        note.getKeywords(),
                        note.getCoverImage(),
                        note.getCategoryId() == null ? null : categoryNameMap.get(note.getCategoryId()),
                        authorNameMap.getOrDefault(note.getUserId(), "未知用户"),
                        note.getCreateTime(),
                        note.getUpdateTime()
                ))
                .collect(Collectors.toList());
    }
}
