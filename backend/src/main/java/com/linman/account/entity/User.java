package com.linman.account.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "USER";          // USER / ADMIN

    @Column(nullable = false)
    private String status = "ENABLED";     // ENABLED / DISABLED

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
