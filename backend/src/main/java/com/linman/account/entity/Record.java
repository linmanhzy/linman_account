package com.linman.account.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "record")
public class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String type;                 // income / expense

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "category_l1", length = 50)
    private String categoryL1;

    @Column(name = "category_l2", length = 50)
    private String categoryL2;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
