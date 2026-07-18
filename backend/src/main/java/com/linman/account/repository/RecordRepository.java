package com.linman.account.repository;

import com.linman.account.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecordRepository extends JpaRepository<Record, Long> {
    List<Record> findByUserIdOrderByRecordDateDescIdDesc(Long userId);

    List<Record> findByUserIdAndTypeOrderByRecordDateDescIdDesc(Long userId, String type);

    List<Record> findByUserIdAndRecordDateBetweenOrderByRecordDateDescIdDesc(
            Long userId, LocalDate start, LocalDate end);

    List<Record> findByUserIdAndTypeAndRecordDateBetweenOrderByRecordDateDescIdDesc(
            Long userId, String type, LocalDate start, LocalDate end);
}
