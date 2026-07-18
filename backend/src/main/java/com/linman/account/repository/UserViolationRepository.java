package com.linman.account.repository;

import com.linman.account.entity.UserViolation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserViolationRepository extends JpaRepository<UserViolation, Long> {
    Optional<UserViolation> findByUserIdAndViolationType(Long userId, String violationType);
}
