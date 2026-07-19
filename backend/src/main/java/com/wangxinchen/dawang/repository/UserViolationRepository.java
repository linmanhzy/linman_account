package com.wangxinchen.dawang.repository;

import com.wangxinchen.dawang.entity.UserViolation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserViolationRepository extends JpaRepository<UserViolation, Long> {
    Optional<UserViolation> findByUserIdAndViolationType(Long userId, String violationType);
}
