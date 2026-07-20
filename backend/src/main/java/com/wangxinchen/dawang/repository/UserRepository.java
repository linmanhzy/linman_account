package com.wangxinchen.dawang.repository;

import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndStatus(Role role, UserStatus status);

    long countByCreatedAtLessThanEqual(LocalDateTime createdAt);

    List<User> findByRole(Role role);

    /**
     * 原子标记首次登录欢迎已发送，仅当尚未标记时才更新。
     * 返回 1 表示本次成功标记（首次），返回 0 表示已被其他请求抢先标记（并发安全）。
     * 自带事务，不依赖调用方的事务上下文。
     */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.firstLoginGreetingSent = true WHERE u.id = :userId AND u.firstLoginGreetingSent = false")
    int markFirstLoginGreetingSent(@Param("userId") Long userId);
}
