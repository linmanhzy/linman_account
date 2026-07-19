-- 记账大王 数据库初始化脚本
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
    `last_login_at` DATETIME,
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
    `type`         VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `scheduled_notification` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `title`          VARCHAR(100) NOT NULL,
    `content`        VARCHAR(1000) NOT NULL,
    `frequency`      VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    `send_time`      TIME,
    `send_date`      DATE,
    `type`           VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
    `target_user_id` BIGINT,
    `last_fire_date` DATE,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `game_score` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `score`        INT          NOT NULL DEFAULT 0,
    `played_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_game_score_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- M2：为 category 表补充图标与排序字段（幂等，可重复执行）
SET @db = DATABASE();
SET @has_icon = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'category' AND COLUMN_NAME = 'icon');
SET @sql_icon = IF(@has_icon = 0, 'ALTER TABLE category ADD COLUMN icon VARCHAR(10) AFTER type', 'SELECT 1');
PREPARE stmt FROM @sql_icon;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_sort = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'category' AND COLUMN_NAME = 'sort_order');
SET @sql_sort = IF(@has_sort = 0, 'ALTER TABLE category ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER parent_id', 'SELECT 1');
PREPARE stmt FROM @sql_sort;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_ca = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'category' AND COLUMN_NAME = 'created_at');
SET @sql_ca = IF(@has_ca = 0, 'ALTER TABLE category ADD COLUMN created_at DATETIME AFTER user_id', 'SELECT 1');
PREPARE stmt FROM @sql_ca;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- M6-2: 通知类型字段（兼容旧库）
SET @has_ntype = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'type');
SET @sql_ntype = IF(@has_ntype = 0, "ALTER TABLE notification ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'ADMIN' AFTER is_read", 'SELECT 1');
PREPARE stmt FROM @sql_ntype;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- M6-2: 最后登录时间字段（兼容旧库）
SET @has_ll = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND COLUMN_NAME = 'last_login_at');
SET @sql_ll = IF(@has_ll = 0, "ALTER TABLE user ADD COLUMN last_login_at DATETIME AFTER created_at", 'SELECT 1');
PREPARE stmt FROM @sql_ll;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
