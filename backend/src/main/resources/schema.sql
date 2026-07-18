-- 林蛮记账 数据库初始化脚本
-- 字符集 utf8mb4（完整支持中文与表情），引擎 InnoDB
-- 说明：所有业务表都带 user_id，用于数据隔离（每人只看自己的数据）
-- 本脚本由 Spring Boot 启动时自动执行（CREATE TABLE IF NOT EXISTS，可重复执行）

CREATE TABLE IF NOT EXISTS `user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `username`     VARCHAR(50)  NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `role`         VARCHAR(20)  NOT NULL DEFAULT 'USER',       -- USER / ADMIN
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'ENABLED',    -- ENABLED / DISABLED
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `record` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `type`         VARCHAR(20)  NOT NULL,                       -- income / expense
    `amount`       DECIMAL(12,2) NOT NULL,
    `record_date`  DATE         NOT NULL,
    `category_l1`  VARCHAR(50),
    `category_l2`  VARCHAR(50),
    `note`         VARCHAR(255),
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_record_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `category` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(50)  NOT NULL,
    `type`         VARCHAR(20),
    `parent_id`    BIGINT,
    `is_system`    TINYINT(1)   NOT NULL DEFAULT 0,
    `user_id`      BIGINT,                                -- 为 NULL 表示系统预设分类
    PRIMARY KEY (`id`),
    KEY `idx_category_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `feedback` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `content`      VARCHAR(1000) NOT NULL,
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',    -- PENDING / REPLIED
    `reply`        VARCHAR(1000),
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_feedback_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notification` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `title`        VARCHAR(100) NOT NULL,
    `content`      VARCHAR(1000) NOT NULL,
    `is_read`      TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `game_score` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `score`        INT          NOT NULL DEFAULT 0,
    `played_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_game_score_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
