package com.linman.account.repository;

import com.linman.account.entity.GameScore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {
}
