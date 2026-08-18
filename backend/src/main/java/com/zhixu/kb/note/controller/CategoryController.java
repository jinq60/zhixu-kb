package com.zhixu.kb.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }

    @GetMapping("/page")
    public Result<Page<Category>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        return Result.success(categoryService.page(page, size, keyword));
    }

    @PostMapping
    public Result<Category> create(@Validated @RequestBody Category category) {
        return Result.success(categoryService.create(category));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @Validated @RequestBody Category category) {
        category.setId(id);
        return Result.success(categoryService.update(category));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.remove(id);
        return Result.success(null);
    }
}
