package com.linman.account.service;

import com.linman.account.common.BizException;
import com.linman.account.dto.CategoryNode;
import com.linman.account.dto.CategoryResponse;
import com.linman.account.entity.Category;
import com.linman.account.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** 获取当前用户可见的分类树：系统预设 + 本人自定义 */
    public List<CategoryNode> listTree(Long userId) {
        List<Category> all = categoryRepository.findByUserIdOrUserIdIsNullOrderBySortOrderAscIdAsc(userId);
        Map<Long, CategoryNode> l1Map = new LinkedHashMap<>();
        List<CategoryNode> roots = new ArrayList<>();

        for (Category c : all) {
            if (c.getParentId() == null) {
                CategoryNode node = toNode(c);
                node.setChildren(new ArrayList<>());
                l1Map.put(c.getId(), node);
                roots.add(node);
            }
        }
        for (Category c : all) {
            if (c.getParentId() != null && l1Map.containsKey(c.getParentId())) {
                l1Map.get(c.getParentId()).getChildren().add(toNode(c));
            }
        }
        return roots;
    }

    /** 新增大类(L1)：parentId 为 null */
    public CategoryResponse createL1(Long userId, String name, String type, String icon) {
        if (name == null || name.isBlank()) {
            throw new BizException(400, "大类名称不能为空");
        }
        if (!"income".equals(type) && !"expense".equals(type)) {
            throw new BizException(400, "类型必须是 income 或 expense");
        }
        if (categoryRepository.findByUserIdAndParentIdIsNullAndNameAndType(userId, name.trim(), type).isPresent()
                || categoryRepository.findByUserIdIsNullAndParentIdIsNullAndNameAndType(name.trim(), type).isPresent()) {
            throw new BizException(409, "该大类已存在");
        }
        Category c = new Category();
        c.setName(name.trim());
        c.setType(type);
        c.setIcon(icon);
        c.setParentId(null);
        c.setSystem(false);
        c.setUserId(userId);
        c.setSortOrder((int) categoryRepository.countByUserIdAndParentIdIsNullAndType(userId, type));
        c.setCreatedAt(LocalDateTime.now());
        return toDto(categoryRepository.save(c));
    }

    /** 新增小类(L2)：parentId 指向可见大类 */
    public CategoryResponse createL2(Long userId, Long parentId, String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(400, "小类名称不能为空");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new BizException(404, "所属大类不存在"));
        if (parent.getUserId() != null && !parent.getUserId().equals(userId)) {
            throw new BizException(403, "无权限在该分类下添加");
        }
        if (categoryRepository.findByUserIdAndParentIdAndNameAndType(userId, parentId, name.trim(), parent.getType()).isPresent()
                || categoryRepository.findByUserIdIsNullAndParentIdAndNameAndType(parentId, name.trim(), parent.getType()).isPresent()) {
            throw new BizException(409, "该小类已存在");
        }
        Category c = new Category();
        c.setName(name.trim());
        c.setType(parent.getType());
        c.setParentId(parentId);
        c.setSystem(false);
        c.setUserId(userId);
        c.setSortOrder(0);
        c.setCreatedAt(LocalDateTime.now());
        return toDto(categoryRepository.save(c));
    }

    /** 修改分类（仅本人自定义） */
    public CategoryResponse update(Long userId, Long id, String name, String icon) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "分类不存在"));
        if (c.isSystem()) {
            throw new BizException(403, "系统预设分类不可修改");
        }
        if (c.getUserId() == null || !c.getUserId().equals(userId)) {
            throw new BizException(403, "无权限修改该分类");
        }
        if (name != null && !name.isBlank()) {
            c.setName(name.trim());
        }
        if (icon != null) {
            c.setIcon(icon);
        }
        return toDto(categoryRepository.save(c));
    }

    /** 删除分类（仅本人自定义；删除大类会同时删除其小类）
     *  TODO: 删除前应提示用户该分类下关联的记账记录将被保留（categoryL1/categoryL2 字段变为孤立文本），
     *  或提供「迁移到其他分类」的选项。
     */
    public void delete(Long userId, Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "分类不存在"));
        if (c.isSystem()) {
            throw new BizException(403, "系统预设分类不可删除");
        }
        if (c.getUserId() == null || !c.getUserId().equals(userId)) {
            throw new BizException(403, "无权限删除该分类");
        }
        // 注意：删除分类后，已有记账记录中引用的 categoryL1/categoryL2 字段保持不变
        if (c.getParentId() == null) {
            categoryRepository.findByParentId(id)
                    .forEach(child -> categoryRepository.deleteById(child.getId()));
        }
        categoryRepository.deleteById(id);
    }

    private CategoryNode toNode(Category c) {
        CategoryNode n = new CategoryNode();
        n.setId(c.getId());
        n.setName(c.getName());
        n.setType(c.getType());
        n.setIcon(c.getIcon());
        n.setSystem(c.isSystem());
        n.setChildren(new ArrayList<>());
        return n;
    }

    private CategoryResponse toDto(Category c) {
        CategoryResponse d = new CategoryResponse();
        d.setId(c.getId());
        d.setName(c.getName());
        d.setType(c.getType());
        d.setIcon(c.getIcon());
        d.setParentId(c.getParentId());
        d.setSortOrder(c.getSortOrder());
        d.setSystem(c.isSystem());
        d.setUserId(c.getUserId());
        d.setCreatedAt(c.getCreatedAt());
        return d;
    }
}
