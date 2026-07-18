package com.linman.account.repository;

import com.linman.account.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    // 当前用户可见：自己创建的 + 系统预设(userId 为 null)
    List<Category> findByUserIdOrUserIdIsNullOrderBySortOrderAscIdAsc(Long userId);

    // 用户自定义大类查重
    Optional<Category> findByUserIdAndParentIdIsNullAndNameAndType(Long userId, String name, String type);

    // 系统预设大类查重
    Optional<Category> findByUserIdIsNullAndParentIdIsNullAndNameAndType(String name, String type);

    // 用户自定义小类查重
    Optional<Category> findByUserIdAndParentIdAndNameAndType(Long userId, Long parentId, String name, String type);

    // 系统预设小类查重
    Optional<Category> findByUserIdIsNullAndParentIdAndNameAndType(Long parentId, String name, String type);

    List<Category> findByParentId(Long parentId);

    long countByUserIdIsNull();

    long countByUserIdAndParentIdIsNullAndType(Long userId, String type);
}
