package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.CategoryMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final NoteMapper noteMapper;

    public List<Category> list() {
        Long userId = getUserIdOrThrow();
        return categoryMapper.selectList(new QueryWrapper<Category>().lambda()
                .eq(Category::getUserId, userId)
                .eq(Category::getIsDeleted, 0)
                .orderByAsc(Category::getSortOrder)
                .orderByDesc(Category::getCreateTime));
    }

    public Page<Category> page(int page, int size, String keyword) {
        Long userId = getUserIdOrThrow();
        LambdaQueryWrapper<Category> wrapper = new QueryWrapper<Category>().lambda()
                .eq(Category::getUserId, userId)
                .eq(Category::getIsDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Category::getName, keyword)
                    .or().like(Category::getDescription, keyword));
        }
        wrapper.orderByAsc(Category::getSortOrder)
                .orderByDesc(Category::getCreateTime);
        return categoryMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Transactional
    public Category create(Category category) {
        Long userId = getUserIdOrThrow();
        String name = normalizeCategoryName(category.getName());
        validateNameUnique(userId, name, null);

        category.setUserId(userId);
        category.setName(name);
        category.setDescription(normalizeDescription(category.getDescription()));
        category.setSortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder());
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public Category update(Category category) {
        Category db = findOwn(category.getId());
        String name = normalizeCategoryName(category.getName());
        validateNameUnique(db.getUserId(), name, db.getId());

        db.setName(name);
        db.setDescription(normalizeDescription(category.getDescription()));
        db.setSortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder());
        categoryMapper.updateById(db);
        return db;
    }

    @Transactional
    public void remove(Long id) {
        Category db = findOwn(id);
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getUserId, db.getUserId())
                .eq(Note::getCategoryId, db.getId())
                .set(Note::getCategoryId, null));
        int rows = categoryMapper.deleteById(db.getId());
        if (rows <= 0) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "Delete category failed");
        }
    }

    private void validateNameUnique(Long userId, String name, Long excludeId) {
        Long exists = categoryMapper.selectCount(new QueryWrapper<Category>().lambda()
                .eq(Category::getUserId, userId)
                .eq(Category::getName, name)
                .eq(Category::getIsDeleted, 0)
                .ne(excludeId != null, Category::getId, excludeId));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Category name already exists");
        }
    }

    private String normalizeCategoryName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Category name must not be empty");
        }
        return rawName.trim();
    }

    private String normalizeDescription(String rawDescription) {
        if (!StringUtils.hasText(rawDescription)) {
            return null;
        }
        return rawDescription.trim();
    }

    private Category findOwn(Long id) {
        Long userId = getUserIdOrThrow();
        Category category = categoryMapper.selectById(id);
        if (category == null || !category.getUserId().equals(userId) || (category.getIsDeleted() != null && category.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Category not found");
        }
        return category;
    }

    private Long getUserIdOrThrow() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Unauthorized");
        }
        return userId;
    }
}
