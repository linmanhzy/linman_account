package com.wangxinchen.dawang.service;

import com.wangxinchen.dawang.common.BizException;
import com.wangxinchen.dawang.dto.CategoryNode;
import com.wangxinchen.dawang.dto.CategoryResponse;
import com.wangxinchen.dawang.entity.Category;
import com.wangxinchen.dawang.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CategoryService 单元测试（Mockito 风格，纯单元）
 *
 * 覆盖：listTree（系统+用户混合）、createL1/createL2（鉴权/查重/参数）、
 *      update（系统保护/所有权）、delete（级联删除子类）。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService categoryService;

    private Category systemL1(Long id, String name, String type) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setIcon("icon-" + id);
        c.setParentId(null);
        c.setSystem(true);
        c.setUserId(null);
        c.setSortOrder(0);
        return c;
    }

    private Category userL1(Long id, String name, String type, Long userId) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setParentId(null);
        c.setSystem(false);
        c.setUserId(userId);
        c.setSortOrder(0);
        return c;
    }

    private Category l2(Long id, String name, Long parentId, String type, Long userId, boolean system) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setParentId(parentId);
        c.setSystem(system);
        c.setUserId(system ? null : userId);
        c.setSortOrder(0);
        return c;
    }

    // ============ listTree ============

    @Test
    void listTree_shouldGroupChildrenUnderParentAndPreserveOrder() {
        // 2 个 L1（1 系统 + 1 用户），每个 L1 下挂 1 个 L2
        Category sysL1 = systemL1(1L, "餐饮", "expense");
        Category userL1 = userL1(2L, "我的副业", "income", 7L);
        Category sysL2 = l2(10L, "午餐", 1L, "expense", null, true);
        Category userL2 = l2(20L, "咨询费", 2L, "income", 7L, false);

        // repo 返回顺序：按 sortOrder + id 升序（L1 在前、L2 紧跟其父节点）
        when(categoryRepository.findByUserIdOrUserIdIsNullOrderBySortOrderAscIdAsc(7L))
                .thenReturn(List.of(sysL1, userL1, sysL2, userL2));

        List<CategoryNode> tree = categoryService.listTree(7L);

        assertEquals(2, tree.size());
        assertEquals("餐饮", tree.get(0).getName());
        assertEquals("我的副业", tree.get(1).getName());

        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("午餐", tree.get(0).getChildren().get(0).getName());
        assertEquals(1, tree.get(1).getChildren().size());
        assertEquals("咨询费", tree.get(1).getChildren().get(0).getName());
    }

    @Test
    void listTree_orphanL2ShouldBeDropped() {
        // 异常的 L2（parentId=99，但 99 不在结果里）→ 必须被丢弃而不是挂在错误的 L1 下
        Category l1 = systemL1(1L, "餐饮", "expense");
        Category orphan = l2(10L, "孤儿", 99L, "expense", null, true);
        when(categoryRepository.findByUserIdOrUserIdIsNullOrderBySortOrderAscIdAsc(7L))
                .thenReturn(List.of(l1, orphan));

        List<CategoryNode> tree = categoryService.listTree(7L);

        assertEquals(1, tree.size());
        assertTrue(tree.get(0).getChildren().isEmpty(), "找不到父节点的 L2 必须被丢弃");
    }

    // ============ createL1 ============

    @Test
    void createL1_blankName_shouldThrow400() {
        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL1(7L, "  ", "expense", "x"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("名称不能为空"));
    }

    @Test
    void createL1_invalidType_shouldThrow400() {
        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL1(7L, "杂项", "transfer", "x"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("income 或 expense"));
    }

    @Test
    void createL1_duplicateInUserScope_shouldThrow409() {
        when(categoryRepository.findByUserIdAndParentIdIsNullAndNameAndType(7L, "餐饮", "expense"))
                .thenReturn(Optional.of(new Category()));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL1(7L, "餐饮", "expense", "🍜"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已存在"));

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createL1_duplicateInSystemScope_shouldThrow409() {
        // 用户 scope 没找到，但系统 scope 找到了 → 也算重名
        when(categoryRepository.findByUserIdAndParentIdIsNullAndNameAndType(7L, "餐饮", "expense"))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserIdIsNullAndParentIdIsNullAndNameAndType("餐饮", "expense"))
                .thenReturn(Optional.of(new Category()));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL1(7L, "餐饮", "expense", "🍜"));
        assertEquals(409, ex.getCode());
    }

    @Test
    void createL1_success_shouldSetSortOrderToCurrentCount() {
        when(categoryRepository.findByUserIdAndParentIdIsNullAndNameAndType(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserIdIsNullAndParentIdIsNullAndNameAndType(any(), any()))
                .thenReturn(Optional.empty());
        when(categoryRepository.countByUserIdAndParentIdIsNullAndType(7L, "expense")).thenReturn(3L);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        CategoryResponse resp = categoryService.createL1(7L, "新大类", "expense", "🎯");

        assertEquals(99L, resp.getId());
        assertEquals("新大类", resp.getName());
        assertEquals("expense", resp.getType());
        assertEquals(7L, resp.getUserId());
        assertFalse(resp.isSystem());

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertEquals(3, saved.getValue().getSortOrder(),
                "sortOrder 应该是当前用户该 type 的 L1 数量（追加到末尾）");
    }

    // ============ createL2 ============

    @Test
    void createL2_parentMissing_shouldThrow404() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL2(7L, 404L, "子项"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void createL2_parentOwnedByOtherUser_shouldThrow403() {
        Category otherUserParent = userL1(1L, "别人的大类", "expense", 999L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(otherUserParent));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL2(7L, 1L, "尝试添加"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void createL2_systemParentOwnedByNull_shouldAllow() {
        // 父类属于系统（userId=null），所有人都能挂小类
        Category sysParent = systemL1(1L, "餐饮", "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sysParent));
        when(categoryRepository.findByUserIdAndParentIdAndNameAndType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserIdIsNullAndParentIdAndNameAndType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(88L);
            return c;
        });

        CategoryResponse resp = categoryService.createL2(7L, 1L, "火锅");
        assertEquals(88L, resp.getId());
        assertEquals(7L, resp.getUserId());
        assertFalse(resp.isSystem());
        assertEquals(1L, resp.getParentId());
    }

    @Test
    void createL2_duplicate_shouldThrow409() {
        Category sysParent = systemL1(1L, "餐饮", "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sysParent));
        when(categoryRepository.findByUserIdAndParentIdAndNameAndType(7L, 1L, "火锅", "expense"))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByUserIdIsNullAndParentIdAndNameAndType(1L, "火锅", "expense"))
                .thenReturn(Optional.of(new Category()));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.createL2(7L, 1L, "火锅"));
        assertEquals(409, ex.getCode());
    }

    // ============ update ============

    @Test
    void update_systemCategory_shouldThrow403() {
        Category sys = systemL1(1L, "餐饮", "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sys));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.update(7L, 1L, "新名", "🎨"));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("系统预设"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_ownedByOtherUser_shouldThrow403() {
        Category other = userL1(1L, "别人的", "expense", 999L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(other));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.update(7L, 1L, "新名", "🎨"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void update_partialUpdate_shouldOnlyModifyProvidedFields() {
        Category mine = userL1(1L, "原名", "expense", 7L);
        mine.setIcon("原图标");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 只传 name 不传 icon
        CategoryResponse resp = categoryService.update(7L, 1L, "新名", null);

        assertEquals("新名", resp.getName());
        assertEquals("原图标", resp.getIcon(), "icon 为 null 时不应被覆盖");
    }

    // ============ delete ============

    @Test
    void deleteL1_shouldAlsoDeleteChildL2s() {
        Category l1 = userL1(1L, "父类", "expense", 7L);
        Category child1 = l2(10L, "子1", 1L, "expense", 7L, false);
        Category child2 = l2(11L, "子2", 1L, "expense", 7L, false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(l1));
        when(categoryRepository.findByParentId(1L)).thenReturn(List.of(child1, child2));

        categoryService.delete(7L, 1L);

        verify(categoryRepository).deleteById(10L);
        verify(categoryRepository).deleteById(11L);
        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteL2_shouldNotTouchOtherCategories() {
        Category child = l2(10L, "子1", 1L, "expense", 7L, false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(child));

        categoryService.delete(7L, 10L);

        // 不应查 findByParentId（L1 才需要级联）
        verify(categoryRepository, never()).findByParentId(anyLong());
        verify(categoryRepository).deleteById(10L);
        verify(categoryRepository, never()).deleteById(1L);
    }

    @Test
    void delete_systemCategory_shouldThrow403() {
        Category sys = systemL1(1L, "餐饮", "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sys));

        BizException ex = assertThrows(BizException.class,
                () -> categoryService.delete(7L, 1L));
        assertEquals(403, ex.getCode());
        verify(categoryRepository, never()).deleteById(anyLong());
    }
}
