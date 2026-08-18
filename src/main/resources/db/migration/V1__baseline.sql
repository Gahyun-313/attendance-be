-- Flyway 도입 시점(2026-08-17) 베이스라인. 기존 db/init.sql을 그대로 이관.
-- 이미 스키마가 있는 환경은 baseline-on-migrate로 실행 없이 V1로만 표시됨.

-- =============================================
-- 0. organizations 테이블 (멀티테넌시 기준 단위)
-- =============================================
CREATE TABLE IF NOT EXISTS organizations (
                                             id          BIGINT          NOT NULL AUTO_INCREMENT,
                                             name        VARCHAR(100)    NOT NULL,
                                             code        VARCHAR(50)     NOT NULL,           -- 소셜 로그인 셀프 조인용 초대 코드
                                             active      TINYINT(1)      NOT NULL DEFAULT 1,
                                             auto_absent_enabled              TINYINT(1)  NOT NULL DEFAULT 1,   -- 결석 자동 처리
                                             nfc_location_validation_enabled  TINYINT(1)  NOT NULL DEFAULT 0,   -- NFC 태그 위치 검증
                                             default_attendance_grace_minutes INT         NOT NULL DEFAULT 5,   -- 기본 출석 인정 시간(분, 프리필용)
                                             default_late_threshold_minutes   INT         NOT NULL DEFAULT 10,  -- 기본 지각 기준 시각(분, 프리필용)
                                             created_at  DATETIME        NOT NULL,
                                             updated_at  DATETIME        NOT NULL,

                                             PRIMARY KEY (id),
                                             UNIQUE KEY uk_organizations_code (code)
);

-- =============================================
-- 1. users 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS users (
                                     id                  BIGINT          NOT NULL AUTO_INCREMENT,
                                     username            VARCHAR(50)     NOT NULL,
                                     password            VARCHAR(255),
                                     email               VARCHAR(100),               -- 선택 필드 (ADMIN은 소셜 로그인, STUDENT는 관리자가 생성)
                                     name                VARCHAR(100)    NOT NULL,
                                     phone               VARCHAR(20),
                                     role                VARCHAR(20)     NOT NULL DEFAULT 'STUDENT',
                                     organization_id     BIGINT          NOT NULL,   -- 소속 단체 (organizations FK)
                                     provider            VARCHAR(20),                -- 소셜 로그인 제공자 (GOOGLE/KAKAO), 비밀번호 로그인은 NULL
                                     provider_id         VARCHAR(100),               -- 소셜 로그인 제공자가 부여한 사용자 식별자
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
                                     INDEX idx_users_role (role),
                                     INDEX idx_users_organization (organization_id),
                                     CONSTRAINT fk_users_organization
                                         FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

-- =============================================
-- 2. refresh_tokens 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id          BIGINT          NOT NULL AUTO_INCREMENT,
                                              user_id     BIGINT          NOT NULL,
                                              token       VARCHAR(512)    NOT NULL,
                                              expires_at  DATETIME        NOT NULL,
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
                                        organization_id BIGINT NOT NULL,                -- 소속 단체
                                        uid          VARCHAR(50)     NOT NULL,
                                        name         VARCHAR(100)    NOT NULL,
                                        description  VARCHAR(255),
                                        location     VARCHAR(100),
                                        status       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
                                        last_used_at DATETIME,
                                        created_at   DATETIME        NOT NULL,
                                        updated_at   DATETIME        NOT NULL,

                                        PRIMARY KEY (id),
                                        UNIQUE KEY uk_nfc_tags_uid (uid),
                                        INDEX idx_nfc_uid (uid),
                                        INDEX idx_nfc_status (status),
                                        INDEX idx_nfc_tags_organization (organization_id),
                                        CONSTRAINT fk_nfc_tags_organization
                                            FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

-- =============================================
-- 4. attendance_sessions 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS attendance_sessions (
                                                   id                      BIGINT          NOT NULL AUTO_INCREMENT,
                                                   organization_id         BIGINT          NOT NULL,
                                                   title                   VARCHAR(200)    NOT NULL,
                                                   description             VARCHAR(500),
                                                   group_name              VARCHAR(100),
                                                   session_date            DATE,
                                                   start_time              DATETIME        NOT NULL,
                                                   end_time                DATETIME        NOT NULL,
                                                   late_threshold_minutes  INT             NOT NULL DEFAULT 10,
                                                   location                VARCHAR(100),
                                                   status                  VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
                                                   nfc_tag_id              BIGINT,
                                                   note                    VARCHAR(500),
                                                   created_by              BIGINT          NOT NULL,
                                                   created_at              DATETIME        NOT NULL,
                                                   updated_at              DATETIME        NOT NULL,

                                                   PRIMARY KEY (id),
                                                   INDEX idx_session_status_start (status, start_time),
                                                   INDEX idx_session_dates (start_time, end_time),
                                                   INDEX idx_session_date (session_date),
                                                   INDEX idx_session_group (group_name),
                                                   INDEX idx_session_organization (organization_id),
                                                   CONSTRAINT fk_session_nfc_tag
                                                       FOREIGN KEY (nfc_tag_id) REFERENCES nfc_tags (id)
                                                           ON DELETE SET NULL,
                                                   CONSTRAINT fk_session_organization
                                                       FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

-- =============================================
-- 5. attendance_records 테이블
-- =============================================
CREATE TABLE IF NOT EXISTS attendance_records (
                                                  id              BIGINT          NOT NULL AUTO_INCREMENT,
                                                  user_id         BIGINT          NOT NULL,
                                                  session_id      BIGINT          NOT NULL,
                                                  status          VARCHAR(20)     NOT NULL DEFAULT 'WAITING',
                                                  check_in_time   DATETIME,
                                                  nfc_tag_uid     VARCHAR(100),
                                                  nfc_location    VARCHAR(100),
                                                  modified_by     VARCHAR(100),
                                                  modify_reason   VARCHAR(500),
                                                  note            VARCHAR(500),
                                                  created_at      DATETIME        NOT NULL,
                                                  updated_at      DATETIME        NOT NULL,

                                                  PRIMARY KEY (id),
                                                  UNIQUE KEY uk_attendance_user_session (user_id, session_id),
                                                  INDEX idx_attendance_user_id (user_id),
                                                  INDEX idx_attendance_session_id (session_id),
                                                  INDEX idx_attendance_status (status),
                                                  INDEX idx_attendance_user_checkin (user_id, check_in_time),
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
                                          device_type VARCHAR(20),
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
                                             target_group  VARCHAR(100),
                                             status        VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
                                             scheduled_at  DATETIME,
                                             sent_at       DATETIME,
                                             target_count  INT,
                                             created_by    BIGINT          NOT NULL,
                                             created_at    DATETIME        NOT NULL,
                                             updated_at    DATETIME,

                                             PRIMARY KEY (id),
                                             INDEX idx_notification_status (status),
                                             INDEX idx_notification_target_group (target_group)
);

-- =============================================
-- 초기 데이터 (기본 단체)
-- =============================================
INSERT INTO organizations (id, name, code, active, created_at, updated_at)
VALUES (
           1,
           '기본 단체',
           'ATT-DEFAULT',
           1,
           NOW(),
           NOW()
       );

-- =============================================
-- 초기 데이터 (관리자 계정)
-- password: admin1234! (BCrypt 암호화)
-- =============================================
INSERT INTO users (username, password, email, name, role, organization_id, password_changed, active, enabled, created_at, updated_at)
VALUES (
           'admin',
           '$2a$10$gxTvXgSH/32K7H5dzXia/.hCkTl3TtwUOOZsRlKRz44pPVH5guxTu',
           'admin@attendance.com',
           '관리자',
           'ADMIN',
           1,
           1,
           1,
           1,
           NOW(),
           NOW()
       );

-- =============================================
-- 초기 데이터 (테스트용 학생 계정 3개)
-- password: student1! / student2! / student3! (각각 BCrypt 암호화)
-- =============================================
INSERT INTO users (username, password, email, name, role, organization_id, password_changed, active, enabled, created_at, updated_at)
VALUES
    (
        'student1',
        '$2a$10$CXLb3zK6L3M6rcKY9/Z7p.oaCtya98KlQCX4B9hIrU5re4qWHuGee',
        'student1@attendance.com',
        '학생1',
        'STUDENT',
        1,
        0,
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
        1,
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
        1,
        0,
        1,
        1,
        NOW(),
        NOW()
    );

-- =============================================
-- 초기 데이터 (어드민 웹 데모용 학생 계정 10개)
-- =============================================
INSERT INTO users (username, password, email, name, phone, organization_id, student_id, group_name, note, enabled, created_at, updated_at)
VALUES
    ('kimm01', '$2b$10$BMO/ft4SqBT9LHzrODCrBeQQzeC0FDACYyJDgoYQryqaiXMNOocnO', NULL, '김민준', '010-4135-1111', 1, 'kimm01', '1', 'temp', 1, NOW(), NOW()),
    ('syn2n', '$2b$10$HG.3SWEJVjsL7uxT9jlJr.bU5rRV0WgJKqwHg54xW/7pd7eDlY9Nu', NULL, '이서연', '010-6545-2222', 1, 'syn2n', '1', 'temp', 1, NOW(), NOW()),
    ('jihoonn3', '$2b$10$CNyGAsQ0wHyV7nhLlC2Hj.PU/fuagqaijHYZGE8w6WWcEdbYtjTZW', 'ppafsd@attendance.com', '박지훈', '010-2543-3333', 1, 'jihoonn3', '2', 'temp', 1, NOW(), NOW()),
    ('choii4', '$2b$10$JfxGs.dZ5mcybSxULHG9He4.45McBy4Uqqza1vEHr7bvaW1.5neGu', 'choiig25@naver.com', '최서윤', NULL, 1, 'choii4', '2', 'temp', 1, NOW(), NOW()),
    ('kjwwoo', '$2b$10$wcNj79jWadKIcPmaTMLGSOVdOhQUch5k.lRRz7h0vWAjxIxSAdhG6', 'kjw254@naver.com', '강지우', NULL, 1, 'kjwwoo', '3', 'temp', 1, NOW(), NOW()),
    ('jyjoon6', '$2b$10$lwwS3Qc1KR2kRPoFmW.wq.CPmEBELsShRTAu5Hud4UKN.d40Kjema', 'asdfgar2@hanmail.com', '조예준', '010-4523-6666', 1, 'jyjoon6', '3', 'temp', 1, NOW(), NOW()),
    ('ha7ha', '$2b$10$C9LgoFOoMrnuW8dn.yVV9u3b7tQTbHe1i4Y6tIttwimYvWgl.TkdG', 'agf77@daum.net', '윤하은', NULL, 1, 'ha7ha', '4', 'temp', 1, NOW(), NOW()),
    ('yjyj8oh', '$2b$10$s2unWxBTKsqhGoHH.TMHs.P8FskBGfWuXMNWGY5CPkVxv8pB1BUlC', 'ajdsgfh@attendance.com', '오유진', NULL, 1, 'yjyj8oh', '4', 'temp', 1, NOW(), NOW()),
    ('baessh', '$2b$10$ln9071eHqq9YGcs78ciWveaEzrwZeVN/XAWQ5LgZpKfLLo.JW6ZlG', 'bbaf@attendance.com', '배서현', NULL, 1, 'baessh', '5', 'temp', 1, NOW(), NOW()),
    ('daesuu19', '$2b$10$cu7IJsio/atrAxp7joDUze60Bz4ngXXHgi.oqJ7c5G3FnqaBon2Wm', 'kjlhk10@attendance.com', '박대수', NULL, 1, 'daesuu19', '5', 'temp', 1, NOW(), NOW());

-- =============================================
-- 초기 데이터 (테스트용 NFC 태그 1개)
-- =============================================
INSERT INTO nfc_tags (id, organization_id, uid, name, description, location, status, created_at, updated_at)
VALUES (
           1,
           1,
           '00:00:01',
           'nfc_tag1',
           '',
           '101호',
           'ACTIVE',
           NOW(),
           NOW()
       );

-- =============================================
-- 초기 데이터 (어드민 웹 데모용 세션 12개)
-- =============================================
INSERT INTO attendance_sessions
(organization_id, title, description, group_name, session_date, start_time, end_time,
 late_threshold_minutes, location, status, nfc_tag_id, note, created_by, created_at, updated_at)
VALUES
    (1, '네트워크프로토콜', NULL, '1',
     DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 DAY), INTERVAL 9 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 DAY), INTERVAL '10:30' HOUR_MINUTE),
     10, '301호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '데이터구조', NULL, '1',
     DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 2 DAY), INTERVAL 11 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 2 DAY), INTERVAL '12:30' HOUR_MINUTE),
     10, '302호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '지중해문화여행', NULL, '2',
     DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 5 DAY), INTERVAL 13 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 5 DAY), INTERVAL '14:30' HOUR_MINUTE),
     10, '303호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '디지털논리설계', NULL, '2',
     CURDATE(), DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL '2:30' HOUR_MINUTE),
     10, '304호', 'ACTIVE', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, 'C언어', NULL, '3',
     DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 9 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL '10:30' HOUR_MINUTE),
     10, '305호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '모바일프로그래밍', NULL, '3',
     DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 11 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL '12:30' HOUR_MINUTE),
     10, '306호', 'SCHEDULED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '신호및시스템', NULL, '4',
     DATE_SUB(CURDATE(), INTERVAL 4 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 4 DAY), INTERVAL 13 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 4 DAY), INTERVAL '14:30' HOUR_MINUTE),
     10, '307호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '데이터통신', NULL, '4',
     CURDATE(), DATE_SUB(NOW(), INTERVAL 15 MINUTE), DATE_ADD(NOW(), INTERVAL '2:45' HOUR_MINUTE),
     10, '308호', 'ACTIVE', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '딥러닝', NULL, '5',
     DATE_SUB(CURDATE(), INTERVAL 6 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 DAY), INTERVAL 9 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 DAY), INTERVAL '10:30' HOUR_MINUTE),
     10, '309호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '사물인터넷기초', NULL, '5',
     DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL 15 HOUR), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 DAY), INTERVAL '16:30' HOUR_MINUTE),
     10, '310호', 'COMPLETED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '통신이론', NULL, '5',
     DATE_ADD(CURDATE(), INTERVAL 2 DAY), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 9 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL '10:30' HOUR_MINUTE),
     10, '311호', 'SCHEDULED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW()),
    (1, '정보보안', NULL, '5',
     DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 3 DAY), INTERVAL 11 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 3 DAY), INTERVAL '12:30' HOUR_MINUTE),
     10, '312호', 'SCHEDULED', 1, 'temp', (SELECT id FROM users WHERE username = 'admin'), NOW(), NOW());
