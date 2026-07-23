package com.attendance.domain.session.repository;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 출석 세션 Repository */
public interface SessionRepository extends JpaRepository<AttendanceSession, Long> {

  /** 상태별 세션 조회 (페이징) */
  Page<AttendanceSession> findByStatus(SessionStatus status, Pageable pageable);

  /** 그룹별 세션 조회 (페이징) */
  Page<AttendanceSession> findByGroupName(String groupName, Pageable pageable);

  /** 날짜별 세션 조회 */
  List<AttendanceSession> findBySessionDate(LocalDate sessionDate);

  /** 세션명 검색 (페이징) */
  Page<AttendanceSession> findByTitleContaining(String title, Pageable pageable);

  /** 상태 + 세션명 검색 (페이징) */
  Page<AttendanceSession> findByStatusAndTitleContaining(
          SessionStatus status, String title, Pageable pageable);

  // ------------------------------------------------
  // 단체(organizationId) 격리 버전 - GET /api/sessions (ADMIN)에서 사용
  // 위 findByStatus/findByTitleContaining/findByStatusAndTitleContaining은 아직 다른 곳에서
  // 안 쓰지만 회귀 위험을 줄이려고 남겨두고, 조회 API 경로는 아래 organizationId 포함 버전으로 옮긴다.
  // ------------------------------------------------

  /** 단체 내 전체 세션 조회 (페이징, 상태/키워드 필터 없음) */
  Page<AttendanceSession> findByOrganizationId(Long organizationId, Pageable pageable);

  /** 단체 내 상태별 세션 조회 (페이징) */
  Page<AttendanceSession> findByOrganizationIdAndStatus(
          Long organizationId, SessionStatus status, Pageable pageable);

  /** 단체 내 세션명 검색 (페이징) */
  Page<AttendanceSession> findByOrganizationIdAndTitleContaining(
          Long organizationId, String title, Pageable pageable);

  /** 단체 내 상태 + 세션명 검색 (페이징) */
  Page<AttendanceSession> findByOrganizationIdAndStatusAndTitleContaining(
          Long organizationId, SessionStatus status, String title, Pageable pageable);

  /**
   * 현재 활성 세션 조회 - 현재 시각이 startTime ~ endTime 사이이고 ACTIVE 상태인 세션
   * - organizationId 조건 추가: 다른 단체의 활성 세션이 섞여 나오지 않도록 고정
   */
  @Query("""
      SELECT s FROM AttendanceSession s
      WHERE s.organizationId = :organizationId
      AND s.status = 'ACTIVE'
      AND s.startTime <= CURRENT_TIMESTAMP
      AND s.endTime >= CURRENT_TIMESTAMP
      """)
  List<AttendanceSession> findActiveSessions(@Param("organizationId") Long organizationId);

  /**
   * NFC 태그 ID로 현재 활성 세션 조회 - 학생 체크인 시 태그에 연결된 진행 중 세션 역추적에 사용.
   * 정상 운영 시 동일 태그에 동시에 ACTIVE인 세션은 1개여야 하며(SessionService.startSession에서 검증),
   * 혹시 다수가 잡히는 경우를 대비해 startTime 내림차순 정렬 후 서비스에서 첫 번째(가장 최근 시작된 세션)를 사용한다.
   */
  @Query("""
      SELECT s FROM AttendanceSession s
      WHERE s.nfcTag.id = :nfcTagId
      AND s.status = 'ACTIVE'
      AND s.startTime <= CURRENT_TIMESTAMP
      AND s.endTime >= CURRENT_TIMESTAMP
      ORDER BY s.startTime DESC
      """)
  List<AttendanceSession> findActiveSessionsByNfcTagId(@Param("nfcTagId") Long nfcTagId);

  /**
   * 태그ID + 상태로 세션 조회 - 세션 시작(startSession) 시 동일 NFC 태그를 사용하는 다른 ACTIVE 세션이
   * 있는지 검증할 때 사용 (중복 활성 세션 방지)
   */
  List<AttendanceSession> findByNfcTagIdAndStatus(Long nfcTagId, SessionStatus status);

  /** 날짜별 세션 수 - 대시보드 통계(오늘 세션 수)에 사용 */
  long countBySessionDate(LocalDate sessionDate);

  /** 상태별 세션 수 - 전체 통계(overall)의 완료 세션 수 등 집계용 */
  long countByStatus(SessionStatus status);

  /**
   * 그룹별 + 상태별 세션 목록 조회 - 대시보드 통계의 그룹별 출석률 집계에 사용
   * (완료된 세션만 대상으로 그룹별 누적 출석 현황을 계산)
   */
  List<AttendanceSession> findByGroupNameAndStatus(String groupName, SessionStatus status);

  /**
   * 최근 완료된 세션 N건 조회 - 대시보드 통계의 "최근 출석률 트렌드"에 사용
   * (Pageable로 개수 제한, sessionDate/startTime DESC 정렬은 호출부에서 Pageable로 전달)
   */
  List<AttendanceSession> findByStatusOrderBySessionDateDesc(SessionStatus status, Pageable pageable);

  // ------------------------------------------------
  // 단체(organizationId) 격리 버전 - StatisticsService 전용 (위 메서드들은 다른 곳에서 안 쓰지만
  // 회귀 위험을 줄이려고 그대로 남겨두고, 통계 API 경로만 아래 organizationId 포함 버전으로 옮긴다)
  // ------------------------------------------------

  /** 단체 내 전체 세션 수 - 전체 통계(overall)의 총 세션 수 집계용 (위 count()의 단체 격리 버전) */
  long countByOrganizationId(Long organizationId);

  /** 단체 내 상태별 세션 수 - 전체 통계(overall)/대시보드 통계 집계용 */
  long countByOrganizationIdAndStatus(Long organizationId, SessionStatus status);

  /** 단체 내 날짜별 세션 수 - 대시보드 통계(오늘 세션 수) 집계용 */
  long countByOrganizationIdAndSessionDate(Long organizationId, LocalDate sessionDate);

  /** 단체 내 그룹별 + 상태별 세션 목록 조회 - 대시보드 통계의 그룹별 출석률 집계용 */
  List<AttendanceSession> findByOrganizationIdAndGroupNameAndStatus(
          Long organizationId, String groupName, SessionStatus status);

  /** 단체 내 최근 완료된 세션 N건 조회 - 대시보드 통계의 "최근 출석률 트렌드" 집계용 */
  List<AttendanceSession> findByOrganizationIdAndStatusOrderBySessionDateDesc(
          Long organizationId, SessionStatus status, Pageable pageable);
}