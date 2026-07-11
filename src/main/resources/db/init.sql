-- =============================================
-- 출석하자 DB 초기화 스크립트
-- =============================================

CREATE DATABASE IF NOT EXISTS attendance
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE attendance;

-- =============================================
-- 1. users 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    username            VARCHAR(50)     NOT NULL,
    password            VARCHAR(255)    NOT NULL,
    email               VARCHAR(100),               -- 선택 필드 (ADMIN은 소셜 로그인, STUDENT는 관리자가 생성)
    name                VARCHAR(100)    NOT NULL,
    phone               VARCHAR(20),
    role                VARCHAR(20)     NOT NULL DEFAULT 'STUDENT',
    organization        VARCHAR(100),
    student_id          VARCHAR(20),                -- 학번 (학생 전용)
    group_name          VARCHAR(100),               -- 소속 그룹 (예: "A반", "1학년")
    note                VARCHAR(500),               -- 관리자 메모
    fcm_token           VARCHAR(255),               -- FCM 푸시 알림 토큰
    password_changed    TINYINT(1)      NOT NULL DEFAULT 0,  -- 최초 비밀번호 변경 여부
    active              TINYINT(1)      NOT NULL DEFAULT 1,  -- 활성/비활성 상태
    first_attendance_at DATETIME,                   -- 첫 출석 시각
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,  -- Spring Security 계정 활성화 여부
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL,

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
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(512)    NOT NULL,
    expires_at  DATETIME        NOT NULL,           -- 엔티티 필드명과 일치 (expiry_date → expires_at)
    created_at  DATETIME        NOT NULL,

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
    id           BIGINT          NOT NULL AUTO_INCREMENT,
    uid          VARCHAR(50)     NOT NULL,
    name         VARCHAR(100)    NOT NULL,
    description  VARCHAR(255),
    location     VARCHAR(100),
    status       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    last_used_at DATETIME,                          -- 마지막 사용 시각
    created_at   DATETIME        NOT NULL,
    updated_at   DATETIME        NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_nfc_tags_uid (uid),
    INDEX idx_nfc_uid (uid),
    INDEX idx_nfc_status (status)
    );

-- =============================================
-- 4. attendance_sessions 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    title                   VARCHAR(200)    NOT NULL,
    description             VARCHAR(500),
    group_name              VARCHAR(100),           -- 대상 그룹
    session_date            DATE,                   -- 세션 날짜 (날짜별 필터링용)
    start_time              DATETIME        NOT NULL,
    end_time                DATETIME        NOT NULL,
    late_threshold_minutes  INT             NOT NULL DEFAULT 10,  -- 지각 기준 시간(분)
    location                VARCHAR(100),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
    nfc_tag_id              BIGINT,                 -- 연결된 NFC 태그 (nullable: 태그 없이 세션 생성 가능)
    note                    VARCHAR(500),           -- 비고
    created_by              BIGINT          NOT NULL,  -- 세션 생성자 (ADMIN user_id)
    created_at              DATETIME        NOT NULL,
    updated_at              DATETIME        NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_session_status_start (status, start_time),
    INDEX idx_session_dates (start_time, end_time),
    INDEX idx_session_date (session_date),
    INDEX idx_session_group (group_name),
    CONSTRAINT fk_session_nfc_tag
    FOREIGN KEY (nfc_tag_id) REFERENCES nfc_tags (id)
    ON DELETE SET NULL                          -- NFC 태그 삭제 시 세션의 nfc_tag_id를 null로
    );

-- =============================================
-- 5. attendance_records 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS attendance_records (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    session_id      BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'WAITING',
    check_in_time   DATETIME,                       -- WAITING 상태일 때 null 가능
    nfc_tag_uid     VARCHAR(100),                   -- 스캔한 NFC 태그 UID (값 보존용)
    nfc_location    VARCHAR(100),                   -- 스캔 당시 태그 위치
    modified_by     VARCHAR(100),                   -- 관리자 수동 수정 시 수정자 이름
    modify_reason   VARCHAR(500),                   -- 상태 변경 사유
    note            VARCHAR(500),                   -- 비고
    created_at      DATETIME        NOT NULL,
    updated_at      DATETIME        NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_user_session (user_id, session_id),
    INDEX idx_attendance_user_id (user_id),
    INDEX idx_attendance_session_id (session_id),
    INDEX idx_attendance_status (status),
    CONSTRAINT fk_attendance_user
    FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE CASCADE,
    CONSTRAINT fk_attendance_session
    FOREIGN KEY (session_id) REFERENCES attendance_sessions (id)
    ON DELETE CASCADE
    );

-- =============================================
-- 6. fcm_tokens 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS fcm_tokens (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(255)    NOT NULL,
    device_type VARCHAR(20),                        -- AOS/iOS/WEB, 기본값 "AOS"는 애플리케이션 레벨에서 처리
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME,

    PRIMARY KEY (id),
    UNIQUE KEY uk_fcm_tokens_token (token),
    INDEX idx_fcm_user_id (user_id),
    INDEX idx_fcm_token (token)
    );

-- =============================================
-- 7. notifications 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS notifications (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    title         VARCHAR(200)    NOT NULL,
    content       VARCHAR(1000)   NOT NULL,
    target_group  VARCHAR(100),                 -- null이면 전체 학생 대상
    status        VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at  DATETIME,                     -- null이면 생성 즉시 발송 대상
    sent_at       DATETIME,                     -- 발송 처리(SENT/FAILED) 완료 시각
    target_count  INT,                          -- 발송 시점 대상 토큰 수 스냅샷
    created_by    BIGINT          NOT NULL,      -- 생성한 관리자 user_id
    created_at    DATETIME        NOT NULL,
    updated_at    DATETIME,

    PRIMARY KEY (id),
    INDEX idx_notification_status (status),
    INDEX idx_notification_target_group (target_group)
    );

-- =============================================
-- 초기 데이터 (관리자 계정)
-- password: admin1234 (BCrypt 암호화)
-- =============================================
INSERT INTO users (username, password, email, name, role, password_changed, active, enabled, created_at, updated_at)
VALUES (
           'admin',
           '$2a$10$gxTvXgSH/32K7H5dzXia/.hCkTl3TtwUOOZsRlKRz44pPVH5guxTu',
           'admin@attendance.com',
           '관리자',
           'ADMIN',
           1,      -- 관리자는 비밀번호 변경 완료로 처리
           1,
           1,
           NOW(),
           NOW()
       );

-- =============================================
-- 초기 데이터 (테스트용 학생 계정 3개)
-- password: student1! / student2! / student3! (각각 BCrypt 암호화)
-- password_changed = 0 : 실제 학생 계정처럼 "최초 로그인 후 비밀번호 변경 전" 상태로 시딩
-- =============================================
INSERT INTO users (username, password, email, name, role, password_changed, active, enabled, created_at, updated_at)
VALUES
    (
        'student1',
        '$2a$10$CXLb3zK6L3M6rcKY9/Z7p.oaCtya98KlQCX4B9hIrU5re4qWHuGee',
        'student1@attendance.com',
        '학생1',
        'STUDENT',
        1,  -- 학생1은 비밀번호 변경 완료로 처리
        1,
        1,
        NOW(),
        NOW()
    ),
    (
        'student2',
        '$2a$10$I9hCysYXngXFL3CeOkCLYeC84830GBGKx8ExeKKZuK3vgFaGjLtgC',
        'student2@attendance.com',
        '학생2',
        'STUDENT',
        0,
        1,
        1,
        NOW(),
        NOW()
    ),
    (
        'student3',
        '$2a$10$jPxbtKbwp4mfnA54gAI92.uviq/JKxf2Qlas4tci5cxPREvJG2PW6',
        'student3@attendance.com',
        '학생3',
        'STUDENT',
        0,
        1,
        1,
        NOW(),
        NOW()
    );

-- =============================================
-- 초기 데이터 (테스트용 NFC 태그 1개)
-- status를 'ACTIVE'로 넣어야 함 - AttendanceService.checkIn()에서 nfcTag.isActive() 체크를 통과해야 체크인 가능
-- =============================================
INSERT INTO nfc_tags (id, uid, name, description, location, status, created_at, updated_at)
VALUES (
           1,
           '00:00:01',
           'nfc_tag1',
           '',
           '101호',
           'ACTIVE',
           NOW(),
           NOW()
       );