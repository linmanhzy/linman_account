package com.wangxinchen.dawang.repository;

import com.wangxinchen.dawang.entity.Role;
import com.wangxinchen.dawang.entity.User;
import com.wangxinchen.dawang.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndStatus(Role role, UserStatus status);

    long countByCreatedAtLessThanEqual(LocalDateTime createdAt);

    List<User> findByRole(Role role);
}
