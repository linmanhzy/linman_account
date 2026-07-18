package com.linman.account.repository;

import com.linman.account.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Feedback> findAllByOrderByCreatedAtDesc();
}
