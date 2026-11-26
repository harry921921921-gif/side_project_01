-- ════════════════════════════════════════════════════════════════════
-- V1：FitTracker 初始資料庫結構（導入 Flyway 時的「起點快照」）
--
-- 這份檔案的內容，是用導入 Flyway 之前的程式（ddl-auto=update）對一個全新的
-- MySQL 8.0 資料庫啟動一次後，再用 mysqldump 匯出的結構，所以跟既有資料庫完全一致：
--   - 全新的資料庫：Flyway 會執行這支檔案，建出所有表格
--   - 已經有表格的舊資料庫：application.properties 設了 baseline-on-migrate，
--     Flyway 會直接把它標記成「已經是 V1」並跳過這支檔案，不會重建、也不會動到資料
--
-- ⚠️ 規則：這支檔案一旦合併就「不能再改」（Flyway 會比對檢查碼，改了啟動就會失敗）。
--    之後要改資料表結構，請新增 V2__xxx.sql、V3__xxx.sql……
--
-- 索引/外鍵名稱（UKxxxx、FKxxxx）刻意沿用 Hibernate 自動產生的名字，讓「新建的庫」
-- 跟「舊的庫」名稱完全相同，之後的 migration 要刪改某個約束時才不會對不上。
-- 表格不指定字元集，沿用資料庫預設值（跟過去 Hibernate 建表的行為一樣）；
-- 只有 users.email 明確指定 utf8mb4_bin，讓 email 大小寫敏感（見 User.java）。
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `display_name` varchar(255) NOT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `enabled` bit(1) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB;

CREATE TABLE `body_part` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `order_index` int NOT NULL,
  `preset` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgx79iivke5bygfy32oqoq2ghq` (`name`)
) ENGINE=InnoDB;

CREATE TABLE `exercise` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body_part` varchar(255) NOT NULL,
  `category` varchar(255) NOT NULL,
  `movement` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `order_index` int DEFAULT NULL,
  `preset` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr1ox34byx8pj01qd39plfobsj` (`name`),
  KEY `FKj7jppp3blmj6qelyj2qljinq2` (`created_by`),
  CONSTRAINT `FKj7jppp3blmj6qelyj2qljinq2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `body_weight` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body_fat_pct` double DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `recorded_date` date NOT NULL,
  `skeletal_muscle_kg` double DEFAULT NULL,
  `time_of_day` varchar(255) DEFAULT NULL,
  `weight_kg` double NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_body_weight_user_date` (`user_id`,`recorded_date`),
  CONSTRAINT `FKndjc1pmdmm52ngin0konesji3` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `email_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  `used` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjpt3yx7gldvj3lhdn9g3b00iq` (`token`),
  KEY `FKpnii9y1irxajhpost9fjubflx` (`user_id`),
  CONSTRAINT `FKpnii9y1irxajhpost9fjubflx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `lift_pr` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exercise_name` varchar(255) NOT NULL,
  `one_rep_max` double DEFAULT NULL,
  `phase_overrides` text,
  `reps` int DEFAULT NULL,
  `rest_seconds` int DEFAULT NULL,
  `sets` int DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `weight_kg` double DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmoaoa4ay7ui1928o90xqp7qke` (`user_id`,`exercise_name`),
  CONSTRAINT `FKl5malkio7pjatkf47x6jarst9` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `training_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body_goal` varchar(255) DEFAULT NULL,
  `card_overrides_json` text,
  `created_at` datetime(6) DEFAULT NULL,
  `days_per_week` int NOT NULL,
  `extra_queue_count` int NOT NULL,
  `mode` enum('NOVICE','VETERAN') NOT NULL,
  `phase_start_date` date NOT NULL,
  `status` varchar(255) NOT NULL,
  `training_weekdays` varchar(120) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKakdfy6q2i5e7jomdkfms446ds` (`user_id`),
  CONSTRAINT `FKakdfy6q2i5e7jomdkfms446ds` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `workout_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body_part` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `workout_date` date NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workout_session_user_date` (`user_id`,`workout_date`),
  CONSTRAINT `FKcufeiyawnkbbkuj235rwsj03a` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE `workout_set` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actual_reps` int DEFAULT NULL,
  `actual_weight` double DEFAULT NULL,
  `completion_status` enum('COMPLETE','DROPPED','FAILED','PAIN') DEFAULT NULL,
  `exercise_name` varchar(255) NOT NULL,
  `reps` int DEFAULT NULL,
  `rest_seconds` int DEFAULT NULL,
  `rpe` double DEFAULT NULL,
  `sets` int DEFAULT NULL,
  `weight_kg` double DEFAULT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workout_set_session_exercise` (`session_id`,`exercise_name`),
  CONSTRAINT `FKlc9s0673svdp19ip133fdcq9u` FOREIGN KEY (`session_id`) REFERENCES `workout_session` (`id`)
) ENGINE=InnoDB;

-- Spring Security「記住我」功能用的表（原本放在 schema.sql），
-- 欄位名稱/型別是 JdbcTokenRepositoryImpl 固定要求的格式，不能改。
CREATE TABLE `persistent_logins` (
  `username` varchar(64) NOT NULL,
  `series` varchar(64) NOT NULL,
  `token` varchar(64) NOT NULL,
  `last_used` timestamp NOT NULL,
  PRIMARY KEY (`series`)
) ENGINE=InnoDB;
