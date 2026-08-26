-- =============================================
-- V3: 그룹(Group) 마스터 테이블 신설
-- =============================================
-- User.groupName/AttendanceSession.groupName은 계속 문자열 필드로 유지하고,
-- 이 테이블을 "존재하는 그룹명" 검증 기준(마스터 목록)으로 사용한다.

CREATE TABLE groups_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_groups_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uk_groups_organization_name UNIQUE (organization_id, name)
);

-- 이미 사용 중인 그룹명을 마스터 목록으로 백필한다.
-- 이게 없으면 배포 직후 기존 그룹명으로 사용자/세션을 생성·수정할 때마다
-- 전부 GROUP_NOT_FOUND로 막히게 된다.
INSERT INTO groups_master (organization_id, name, created_at, updated_at)
SELECT DISTINCT organization_id, group_name, NOW(), NOW()
FROM users
WHERE group_name IS NOT NULL;

-- 세션에만 쓰이고 현재 소속 학생은 없는 그룹명도 놓치지 않도록 세션 쪽도 백필한다.
-- 위에서 이미 들어간 이름은 유니크 제약에 걸리므로 INSERT IGNORE로 건너뛴다.
INSERT IGNORE INTO groups_master (organization_id, name, created_at, updated_at)
SELECT DISTINCT organization_id, group_name, NOW(), NOW()
FROM attendance_sessions
WHERE group_name IS NOT NULL;
