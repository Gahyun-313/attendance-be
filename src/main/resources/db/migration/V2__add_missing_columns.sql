-- =============================================
-- V2: 운영 DB(RDS)와 엔티티 간 스키마 드리프트 수정
-- =============================================
-- V1__baseline.sql이 CREATE TABLE IF NOT EXISTS로 작성돼 있어, 이미 존재하던 운영 테이블
-- (nfc_tags, organizations)에는 이후 엔티티에 추가된 컬럼이 반영되지 않은 채로 남아있었다.
-- 이 마이그레이션이 실제로 빠져있던 컬럼을 채워 엔티티와 스키마를 일치시킨다.

-- =============================================
-- 1. nfc_tags: organization_id 추가
-- =============================================
-- NOT NULL 컬럼을 기존 행이 있는 테이블에 바로 추가할 수 없어 NULL 허용으로 먼저 추가한다.
ALTER TABLE nfc_tags
    ADD COLUMN organization_id BIGINT NULL AFTER id;

-- 기존 행을 소속 단체로 백필한다. 단체가 1개뿐인 현재는 그 단체로 채우면 되지만,
-- 단체가 여러 개인 환경에서 이 마이그레이션을 재사용할 경우 이 값은 다시 검토해야 한다.
UPDATE nfc_tags
SET organization_id = (SELECT id FROM organizations ORDER BY id LIMIT 1)
WHERE organization_id IS NULL;

-- 백필 완료 후 NOT NULL로 전환하고, baseline과 동일한 FK/인덱스를 추가한다.
ALTER TABLE nfc_tags
    MODIFY COLUMN organization_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_nfc_tags_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    ADD INDEX idx_nfc_tags_organization (organization_id);

-- =============================================
-- 2. organizations: 출석 정책 컬럼 4개 추가
-- =============================================
-- 전부 DEFAULT가 정의돼 있어 기존 행에도 안전하게 채워진다.
ALTER TABLE organizations
    ADD COLUMN auto_absent_enabled TINYINT(1) NOT NULL DEFAULT 1
        AFTER active,                                                  -- 결석 자동 처리
    ADD COLUMN nfc_location_validation_enabled TINYINT(1) NOT NULL DEFAULT 0
        AFTER auto_absent_enabled,                                     -- NFC 태그 위치 검증
    ADD COLUMN default_attendance_grace_minutes INT NOT NULL DEFAULT 5
        AFTER nfc_location_validation_enabled,                         -- 기본 출석 인정 시간(분)
    ADD COLUMN default_late_threshold_minutes INT NOT NULL DEFAULT 10
        AFTER default_attendance_grace_minutes;                        -- 기본 지각 기준 시각(분)
