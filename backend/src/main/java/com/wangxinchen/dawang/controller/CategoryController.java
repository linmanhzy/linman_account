package com.wangxinchen.dawang.controller;

import com.wangxinchen.dawang.common.Result;
import com.wangxinchen.dawang.dto.CategoryNode;
import com.wangxinchen.dawang.dto.CategoryRequest;
import com.wangxinchen.dawang.dto.CategoryResponse;
import com.wangxinchen.dawang.dto.CategoryUpdateRequest;
import com.wangxinchen.dawang.security.SecurityHelper;
import com.wangxinchen.dawang.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "获取分类树（系统预设 + 本人自定义）")
    @GetMapping
    public Result<List<CategoryNode>> tree() {
        return Result.ok(categoryService.listTree(SecurityHelper.getCurrentUserId()));
    }

    @Operation(summary = "新增分类（parentId 为空=大类，否则=小类）")
    @PostMapping
    public Result<CategoryResponse> create(@Valid @RequestBody CategoryRequest req) {
        Long uid = SecurityHelper.getCurrentUserId();
        if (req.getParentId() == null) {
            return Result.ok(categoryService.createL1(uid, req.getName(), req.getType(), req.getIcon()));
        }
        return Result.ok(categoryService.createL2(uid, req.getParentId(), req.getName()));
    }

    @Operation(summary = "修改分类（仅本人自定义）")
    @PutMapping("/{id}")
    public Result<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest req) {
        return Result.ok(categoryService.update(SecurityHelper.getCurrentUserId(), id, req.getName(), req.getIcon()));
    }

    @Operation(summary = "删除分类（仅本人自定义；删大类会同时删其小类）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(SecurityHelper.getCurrentUserId(), id);
        return Result.ok();
    }
}
