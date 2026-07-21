-- ============================================================
-- Day 6 Phase 4 - 인덱스 Before/After 성능 비교용 더미 데이터
-- ============================================================
-- 목적: attendance_records를 10만 건 규모로 채워서 (user_id, check_in_time)
-- 복합 인덱스의 효과를 EXPLAIN 실행계획뿐 아니라 실제 응답시간(k6)으로도 확인한다.
--
-- 설계 근거:
--   attendance_records에는 UNIQUE KEY(user_id, session_id) 제약이 있어서,
--   "사용자 20명 x 세션 5,000개" 조합을 전부 채우면 정확히 10만 건이 되고,
--   동시에 user_id 하나당 5,000건이 생겨 findByUserId 정렬 비용을 현실적으로
--   재현할 수 있다 (k6가 student1 계정으로 GET /api/attendances/me를 호출하는
--   시나리오와 맞음).
--
--   AttendanceService.checkIn()의 실제 비즈니스 로직(락, 이벤트 발행, 캐시 무효화
--   등)은 거치지 않고 SQL로 직접 INSERT한다 - 이 데이터는 "쿼리 성능 측정 전용"이라
--   비즈니스 규칙 검증이 불필요하기 때문. student1/2/3 + 신규 perf_student1~17로
--   총 20명을 사용한다.
--
-- 실행 전 주의:
--   - 로컬 개발 DB 전용 (운영 DB에서 절대 실행 금지)
--   - 재실행하면 매번 20명/5,000세션/10만 레코드가 "추가로 더" 쌓인다.
--     다시 채우려면 파일 맨 아래 "정리(clean up)" 블록을 먼저 실행할 것
--   - MySQL 8 기준. cte_max_recursion_depth 기본값(1,000)보다 세션 개수(5,000)가
--     많아서 세션 생성 전에 세션 변수로 한도를 올려준다.
-- ============================================================

USE attendance;

SET SESSION cte_max_recursion_depth = 6000;

-- ---------------------------------------------
-- 1. 더미 학생 계정 17명 추가 (기존 student1~3 + 이 17명 = 총 20명)
-- ---------------------------------------------
-- 실제로 로그인할 일이 없는 채움용 계정이라 student1과 동일한 해시(평문 student1!)를 재사용.
-- note='PERF_DUMMY'로 표시해둬야 나중에 정리(clean up)할 때 식별 가능.
INSERT INTO users
(username, password, email, name, role, group_name, note,
 password_changed, active, enabled, created_at, updated_at)
SELECT
    CONCAT('perf_student', n),
    '$2a$10$CXLb3zK6L3M6rcKY9/Z7p.oaCtya98KlQCX4B9hIrU5re4qWHuGee',
    CONCAT('perf_student', n, '@attendance.com'),
    CONCAT('부하테스트학생', n),
    'STUDENT',
    'PERF_TEST',
    'PERF_DUMMY',
    1, 1, 1, NOW(), NOW()
FROM (
         WITH RECURSIVE seq AS (
             SELECT 1 AS n
             UNION ALL
             SELECT n + 1 FROM seq WHERE n < 17
         )
         SELECT n FROM seq
     ) AS user_seq;

-- ---------------------------------------------
-- 2. 더미 세션 5,000개 추가
-- ---------------------------------------------
-- session_date/start_time을 과거 365일 범위로 흩어서 check_in_time 정렬이
-- 실제로 의미 있게 나오도록 함 (전부 같은 시각이면 filesort 비용 비교가 왜곡됨).
INSERT INTO attendance_sessions
(title, description, group_name, session_date, start_time, end_time,
 late_threshold_minutes, location, status, nfc_tag_id, note, created_by,
 created_at, updated_at)
SELECT
    CONCAT('perf-session-', n),
    'k6 성능 측정용 더미 세션',
    NULL,
    DATE_SUB(CURDATE(), INTERVAL (n % 365) DAY),
    DATE_SUB(NOW(), INTERVAL (n % 365) DAY),
    DATE_ADD(DATE_SUB(NOW(), INTERVAL (n % 365) DAY), INTERVAL 1 HOUR),
    10,
    '101호',
    'COMPLETED',
    1, -- init.sql에서 만든 nfc_tag1 (id=1) 재사용
    'PERF_DUMMY',
    (SELECT id FROM users WHERE username = 'admin'),
    NOW(), NOW()
FROM (
         WITH RECURSIVE seq AS (
             SELECT 1 AS n
             UNION ALL
             SELECT n + 1 FROM seq WHERE n < 5000
         )
         SELECT n FROM seq
     ) AS session_seq;

-- ---------------------------------------------
-- 3. attendance_records 10만 건 = 사용자 20명 x 세션 5,000개 (cross join)
-- ---------------------------------------------
INSERT INTO attendance_records
(user_id, session_id, status, check_in_time, nfc_tag_uid, nfc_location,
 note, created_at, updated_at)
SELECT
    u.id,
    s.id,
    'PRESENT',
    DATE_ADD(s.start_time, INTERVAL FLOOR(RAND() * 20) MINUTE),
    '00:00:01',
    '101호',
    'PERF_DUMMY',
    NOW(), NOW()
FROM
    (SELECT id FROM users WHERE username IN ('student1', 'student2', 'student3')
                             OR note = 'PERF_DUMMY') AS u
        CROSS JOIN
    (SELECT id, start_time FROM attendance_sessions WHERE note = 'PERF_DUMMY') AS s;

-- ---------------------------------------------
-- 확인용 쿼리 (직접 실행해서 10만 건 이상인지 확인)
-- ---------------------------------------------
-- SELECT COUNT(*) FROM attendance_records;
-- SELECT COUNT(*) FROM attendance_records WHERE user_id = (SELECT id FROM users WHERE username='student1');
-- EXPLAIN FORMAT=JSON SELECT * FROM attendance_records WHERE user_id = (SELECT id FROM users WHERE username='student1') ORDER BY check_in_time DESC LIMIT 20;

-- ============================================================
-- 정리(clean up) - 재실행하거나 측정 끝난 뒤 되돌릴 때 사용
-- FK가 ON DELETE CASCADE라 sessions/users만 지워도 연쇄 삭제되지만,
-- 순서를 명시적으로 지켜 안전하게 지운다 (자식 -> 부모 순서).
-- ============================================================
-- DELETE FROM attendance_records WHERE note = 'PERF_DUMMY';
-- DELETE FROM attendance_sessions WHERE note = 'PERF_DUMMY';
-- DELETE FROM users WHERE note = 'PERF_DUMMY';