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
--   - 숫자 시퀀스는 WITH RECURSIVE 대신 "tally table"(0~9 보조 테이블을 여러 번
--     CROSS JOIN해서 자릿수 조합으로 큰 수를 만드는) 방식을 사용한다. 재귀 CTE는
--     MySQL 버전/클라이언트에 따라 파생 테이블(서브쿼리) 안에 중첩했을 때 파싱이
--     안 되는 경우가 있어, 버전 상관없이 항상 동작하는 이 방식으로 바꿨다.
--   - digits 보조 테이블은 TEMPORARY가 아니라 일반 테이블이다. TEMPORARY TABLE은
--     "같은 쿼리 안에서 같은 임시 테이블을 두 번 이상 참조 불가"라는 MySQL 제약이
--     있어(ERROR 1137 Can't reopen table), digits를 d0~d3로 여러 번 조인하는 이
--     스크립트와는 맞지 않는다. 스크립트 끝에서 DROP TABLE로 정리한다.
-- ============================================================

USE attendance;

-- ---------------------------------------------
-- 0. 이전 실행에서 남은 PERF_DUMMY 데이터 정리 (재실행 안전성 확보)
-- ---------------------------------------------
-- 스크립트 중간에 에러가 나서 멈춰도, 그 전에 실행된 INSERT는 이미 커밋된 채로 남는다
-- (MySQL은 "문장 하나"만 실패 시 롤백하지, 스크립트 전체를 하나의 트랜잭션으로 묶어주지
-- 않는다). 그 상태에서 재실행하면 이미 있는 perf_student* 계정과 username이 겹쳐
-- Duplicate entry 에러가 난다. 그래서 매번 스크립트 맨 앞에서 이전 PERF_DUMMY 데이터를
-- 지우고 시작하게 만들어 몇 번을 재실행해도 항상 같은 결과가 나오게(멱등하게) 한다.
-- FK가 ON DELETE CASCADE라 sessions/users만 지워도 attendance_records는 연쇄 삭제되지만,
-- 순서를 명시적으로 지켜 안전하게 지운다 (자식 -> 부모 순서).
DELETE FROM attendance_records WHERE note = 'PERF_DUMMY';
DELETE FROM attendance_sessions WHERE note = 'PERF_DUMMY';
DELETE FROM users WHERE note = 'PERF_DUMMY';

-- 0~9 숫자를 담은 보조 테이블 - 여러 번 CROSS JOIN해서 자릿수 조합으로 시퀀스를 만드는 용도
-- 주의: TEMPORARY TABLE로 만들면 "같은 쿼리 안에서 같은 임시 테이블을 두 번 이상
-- 참조할 수 없다"는 MySQL 제약(ERROR 1137 Can't reopen table)에 걸린다. 아래에서
-- digits를 d0~d3까지 여러 번 CROSS JOIN하므로, 반드시 일반 테이블로 만들어야 한다.
DROP TABLE IF EXISTS digits;
CREATE TABLE digits (d INT);
INSERT INTO digits (d) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

-- ---------------------------------------------
-- 1. 더미 학생 계정 17명 추가 (기존 student1~3 + 이 17명 = 총 20명)
-- ---------------------------------------------
-- 실제로 로그인할 일이 없는 채움용 계정이라 student1과 동일한 해시(평문 student1!)를 재사용.
-- note='PERF_DUMMY'로 표시해둬야 나중에 정리(clean up)할 때 식별 가능.
-- digits 2개 조합(0~99)이면 17까지 충분히 커버됨.
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
         SELECT (d1.d * 10 + d0.d + 1) AS n
         FROM digits d0
                  CROSS JOIN digits d1
     ) AS user_seq
WHERE n <= 17;

-- ---------------------------------------------
-- 2. 더미 세션 5,000개 추가
-- ---------------------------------------------
-- session_date/start_time을 과거 범위로 흩어서 check_in_time 정렬이
-- 실제로 의미 있게 나오도록 함 (전부 같은 시각이면 filesort 비용 비교가 왜곡됨).
-- digits 4개 조합(0~9999)이면 5,000까지 커버됨.
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
         SELECT (d3.d * 1000 + d2.d * 100 + d1.d * 10 + d0.d + 1) AS n
         FROM digits d0
                  CROSS JOIN digits d1
                  CROSS JOIN digits d2
                  CROSS JOIN digits d3
     ) AS session_seq
WHERE n <= 5000;

DROP TABLE IF EXISTS digits;

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
-- 정리(clean up) - 측정이 완전히 끝나서 더미 데이터를 완전히 되돌리고 싶을 때 사용.
-- (재실행 시에는 이 블록을 따로 실행할 필요 없음 - 스크립트 맨 앞의 "0. 정리" 단계가
-- 매번 자동으로 처리해준다)
-- ============================================================
-- DELETE FROM attendance_records WHERE note = 'PERF_DUMMY';
-- DELETE FROM attendance_sessions WHERE note = 'PERF_DUMMY';
-- DELETE FROM users WHERE note = 'PERF_DUMMY';