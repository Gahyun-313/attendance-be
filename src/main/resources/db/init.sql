-- =============================================
-- 출석하자 DB 초기화 스크립트
-- =============================================

-- DB 생성
CREATE DATABASE IF NOT EXISTS attendance
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE attendance;

-- =============================================
-- 1. users 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id    BIGINT    NOT NULL AUTO_INCREMENT,
    username    VARCHAR(20)     NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    email       VARCHAR(100)    NOT NULL,
    name        VARCHAR(50)     NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'STUDENT',
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_username (username),
    INDEX idx_users_role (role)
);

-- =============================================
-- 2. refresh_tokens 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id    BIGINT    NOT NULL AUTO_INCREMENT,
    user_id     BIGINT    NOT NULL,
    token       VARCHAR(512)    NOT NULL,
    expiry_date DATETIME        NOT NULL,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_user_id (user_id),
    UNIQUE KEY uk_refresh_tokens_token (token),
    INDEX idx_refresh_tokens_user_id (user_id),
    CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

-- =============================================
-- 3. nfc_tags 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS nfc_tags (
    id    BIGINT    NOT NULL AUTO_INCREMENT,
    uid         VARCHAR(50)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(255),
    location    VARCHAR(100),
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_nfc_tags_uid (uid),
    INDEX idx_nfc_uid (uid),
    INDEX idx_nfc_status (status)
);

-- =============================================
-- 4. attendance_sessions 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS attendance_sessions (
     id        BIGINT    NOT NULL AUTO_INCREMENT,
     title     VARCHAR(100)    NOT NULL,
     description     VARCHAR(500),
     start_time      DATETIME        NOT NULL,
     end_time        DATETIME        NOT NULL,
     late_threshold  DATETIME        NOT NULL,
     status    VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
     location        VARCHAR(100),
     created_at      DATETIME        NOT NULL,
     updated_at      DATETIME        NOT NULL,

     PRIMARY KEY (id),
     INDEX idx_session_status_start (status, start_time),
     INDEX idx_session_dates (start_time, end_time)
);

-- =============================================
-- 5. attendance_records 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS attendance_records (
    id    BIGINT    NOT NULL AUTO_INCREMENT,
    user_id     BIGINT    NOT NULL,
    session_id  BIGINT    NOT NULL,
    nfc_tag_id  BIGINT    NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    attended_at DATETIME        NOT NULL,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_user_session (user_id, session_id),  -- 중복 출석 방지
    INDEX idx_attendance_user_id (user_id),
    INDEX idx_attendance_session_id (session_id),
    INDEX idx_attendance_status (status),
    CONSTRAINT fk_attendance_user
        FOREIGN KEY (user_id) REFERENCES users (id)
      ON DELETE CASCADE,
    CONSTRAINT fk_attendance_session
        FOREIGN KEY (session_id) REFERENCES attendance_sessions (id)
      ON DELETE CASCADE,
    CONSTRAINT fk_attendance_nfc_tag
        FOREIGN KEY (nfc_tag_id) REFERENCES nfc_tags (id)
      ON DELETE RESTRICT
);

-- =============================================
-- 초기 데이터 (관리자 계정)
-- password: admin1234! (BCrypt 암호화)
-- =============================================
INSERT INTO users (username, password, email, name, role, created_at, updated_at)
VALUES (
     'admin',
     '$2a$10$7EqJtq98hPqEX7fNZaFWoOa9sJmE7zWF2E9G6YFhJyGqEbKMkDPEe',
     'admin@attendance.com',
     '관리자',
     'ADMIN',
     NOW(),
     NOW()
);