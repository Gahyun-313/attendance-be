package com.attendance.domain.session.repository;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 출석 세션에 대한 조회 처리 */
public interface SessionRepository extends JpaRepository<AttendanceSession, Long> {

  /** 상태별 세션 페이징 조회 */
  Page<AttendanceSession> findByStatus(SessionStatus status, Pageable pageable);

  /** 그룹별 세션 페이징 조회 */
  Page<AttendanceSession> findByGroupName(String groupName, Pageable pageable);

  /** 날짜별 세션 조회 */
  List<AttendanceSession> findBySessionDate(LocalDate sessionDate);

  /** 세션명으로 검색 */
  Page<AttendanceSession> findByTitleContaining(String title, Pageable pageable);

  /** 상태와 세션명으로 검색 */
  Page<AttendanceSession> findByStatusAndTitleContaining(
      SessionStatus status, String title, Pageable pageable);

  // 아래부터는 단체(organizationId) 격리 버전. GET /api/sessions (ADMIN)에서 사용.
  // 위 버전들은 다른 곳에서 쓰이진 않지만 회귀 위험 때문에 그대로 남겨둔다.

  /** 단체 내 전체 세션 상태/키워드 필터 없이 페이징 조회 */
  Page<AttendanceSession> findByOrganizationId(Long organizationId, Pageable pageable);

  /** 단체 내 상태별 세션 페이징 조회 */
  Page<AttendanceSession> findByOrganizationIdAndStatus(
      Long organizationId, SessionStatus status, Pageable pageable);

  /** 단체 내 세션명으로 검색 */
  Page<AttendanceSession> findByOrganizationIdAndTitleContaining(
      Long organizationId, String title, Pageable pageable);

  /** 단체 내 상태와 세션명으로 검색 */
  Page<AttendanceSession> findByOrganizationIdAndStatusAndTitleContaining(
      Long organizationId, SessionStatus status, String title, Pageable pageable);

  /** organizationId로 단체를 격리해, startTime~endTime 사이이면서 ACTIVE 상태인 세션 조회 */
  @Query(
      """
          SELECT s FROM AttendanceSession s
          WHERE s.organizationId = :organizationId
          AND s.status = 'ACTIVE'
          AND s.startTime <= CURRENT_TIMESTAMP
          AND s.endTime >= CURRENT_TIMESTAMP
          """)
  List<AttendanceSession> findActiveSessions(@Param("organizationId") Long organizationId);

  /** NFC 태그로 현재 활성 세션 역추적(체크인용). 정상적으로는 1개지만, 여러 개가 잡히면 startTime 내림차순 첫 번째를 쓴다. */
  @Query(
      """
          SELECT s FROM AttendanceSession s
          WHERE s.nfcTag.id = :nfcTagId
          AND s.status = 'ACTIVE'
          AND s.startTime <= CURRENT_TIMESTAMP
          AND s.endTime >= CURRENT_TIMESTAMP
          ORDER BY s.startTime DESC
          """)
  List<AttendanceSession> findActiveSessionsByNfcTagId(@Param("nfcTagId") Long nfcTagId);

  /** 태그ID와 상태로 세션 조회. 세션 시작 시 같은 태그의 중복 ACTIVE 세션을 검증할 때 사용. */
  List<AttendanceSession> findByNfcTagIdAndStatus(Long nfcTagId, SessionStatus status);

  /** 날짜별 세션 수 조회. 대시보드 통계의 오늘 세션 수에 사용. */
  long countBySessionDate(LocalDate sessionDate);

  /** 상태별 세션 수 조회. 전체 통계의 완료 세션 수 등 집계에 사용. */
  long countByStatus(SessionStatus status);

  /** 그룹별+상태별 세션 목록 조회. 대시보드 통계의 그룹별 출석률 집계에 사용하며, 완료된 세션만 대상으로 한다. */
  List<AttendanceSession> findByGroupNameAndStatus(String groupName, SessionStatus status);

  /** 최근 완료된 세션 N건 조회. 대시보드의 최근 출석률 트렌드에 사용하며, 개수/정렬은 Pageable로 전달받는다. */
  List<AttendanceSession> findByStatusOrderBySessionDateDesc(
      SessionStatus status, Pageable pageable);

  // 아래부터는 단체(organizationId) 격리 버전. StatisticsService 전용.

  /** 단체 내 전체 세션 수 조회. 전체 통계의 총 세션 수 집계에 사용하며, count()의 단체 격리 버전이다. */
  long countByOrganizationId(Long organizationId);

  /** 단체 내 상태별 세션 수 조회. 전체 통계/대시보드 통계 집계에 사용. */
  long countByOrganizationIdAndStatus(Long organizationId, SessionStatus status);

  /** 단체 내 날짜별 세션 수 조회. 대시보드 통계의 오늘 세션 수 집계에 사용. */
  long countByOrganizationIdAndSessionDate(Long organizationId, LocalDate sessionDate);

  /** 단체 내 그룹별+상태별 세션 목록 조회. 대시보드 통계의 그룹별 출석률 집계에 사용. */
  List<AttendanceSession> findByOrganizationIdAndGroupNameAndStatus(
      Long organizationId, String groupName, SessionStatus status);

  /** 단체 내 최근 완료된 세션 N건 조회. 대시보드의 최근 출석률 트렌드 집계에 사용. */
  List<AttendanceSession> findByOrganizationIdAndStatusOrderBySessionDateDesc(
      Long organizationId, SessionStatus status, Pageable pageable);

  /** 그룹명 일괄 변경. 그룹 이름 수정 시 해당 그룹을 대상으로 하는 세션들의 문자열 groupName을 동기화하는 데 사용한다. */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE AttendanceSession s SET s.groupName = :newName "
          + "WHERE s.organizationId = :organizationId AND s.groupName = :oldName")
  int renameGroupName(
      @Param("organizationId") Long organizationId,
      @Param("oldName") String oldName,
      @Param("newName") String newName);

  /** 배치(스케줄러) 전용 - 상태/종료시각 기준 세션 조회, 단체 격리 없음 */
  List<AttendanceSession> findByStatusAndEndTimeBefore(SessionStatus status, LocalDateTime endTime);

  /** 그룹명 일괄 초기화(null). 그룹 삭제 시 해당 그룹을 대상으로 하던 세션을 무소속 상태로 되돌리는 데 사용한다. */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE AttendanceSession s SET s.groupName = NULL "
          + "WHERE s.organizationId = :organizationId AND s.groupName = :groupName")
  int clearGroupName(
      @Param("organizationId") Long organizationId, @Param("groupName") String groupName);
}
