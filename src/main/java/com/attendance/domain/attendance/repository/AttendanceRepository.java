package com.attendance.domain.attendance.repository;

import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 출석 기록에 대한 조회 처리 */
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

  /**
   * 동일 user+session 레코드 존재 여부 확인. WAITING 사전 등록 레코드 생성 전 중복 방지용으로 쓰며, DB Unique 제약과 함께 1차 방어선이다.
   */
  boolean existsByUserIdAndSessionId(Long userId, Long sessionId);

  /**
   * user+session으로 기존 레코드 조회. 체크인 시 WAITING 레코드가 있으면 갱신하고, 없으면 신규 생성할 때
   * 사용한다(AttendanceService.checkIn).
   */
  Optional<AttendanceRecord> findByUserIdAndSessionId(Long userId, Long sessionId);

  /**
   * 내 출석 기록 페이징 조회. 앱 "내역" 화면에 사용하며, 정렬은 Controller에서 Pageable로 전달받는다. 한 사용자 기록은 학기/그룹 단위라 개수가
   * 제한적이라 offset pagination으로 충분하다.
   */
  Page<AttendanceRecord> findByUserId(Long userId, Pageable pageable);

  /** 세션별 출석 현황 조회(관리자). 한 세션의 전체 출석 레코드 반환. */
  List<AttendanceRecord> findBySessionId(Long sessionId);

  /**
   * 세션별+상태별 레코드 조회. 세션 종료 시 남은 WAITING을 ABSENT로 일괄 처리할 때
   * 사용(AttendanceService.markAbsentForRemainingWaiting).
   */
  List<AttendanceRecord> findBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

  /** 세션별 전체 출석 레코드 수 조회. 대시보드 집계에 사용. */
  long countBySessionId(Long sessionId);

  /** 세션별 특정 상태 레코드 수 조회. 대시보드의 출석/지각/결석/대기 수 집계에 사용. */
  long countBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

  /** 전체 상태별 레코드 수 조회. 전체 통계의 누적 출석/지각/결석/대기 건수 집계에 사용. */
  long countByStatus(AttendanceStatus status);

  /**
   * 단체별+상태별 레코드 수 조회. countByStatus의 단체 격리 버전이며, AttendanceRecord에는 organizationId가 없어 세션을 통해 간접
   * 소속을 서브쿼리로 판단한다.
   */
  @Query(
      "SELECT COUNT(a) FROM AttendanceRecord a "
          + "WHERE a.status = :status "
          + "AND a.sessionId IN (SELECT s.id FROM AttendanceSession s WHERE s.organizationId = :organizationId)")
  long countByStatusAndOrganizationId(
      @Param("status") AttendanceStatus status, @Param("organizationId") Long organizationId);

  /** 사용자별 전체 출석 레코드 수 조회. 사용자 통계(user/me)의 총 참여 건수 집계에 사용. */
  long countByUserId(Long userId);

  /** 사용자별 특정 상태 레코드 수 조회. 사용자 통계의 출석/지각/결석 건수 집계에 사용. */
  long countByUserIdAndStatus(Long userId, AttendanceStatus status);

  /**
   * 단체 내 최근 체크인 기록 N건 조회(세션 구분 없음). WAITING은 checkInTime이 없어 자연히 제외되며, 체크인 시각 내림차순으로 최신 N건만 가져온다.
   */
  @Query(
      "SELECT a FROM AttendanceRecord a "
          + "WHERE a.checkInTime IS NOT NULL "
          + "AND a.sessionId IN (SELECT s.id FROM AttendanceSession s WHERE s.organizationId = :organizationId) "
          + "ORDER BY a.checkInTime DESC")
  List<AttendanceRecord> findRecentCheckInsByOrganizationId(
      @Param("organizationId") Long organizationId, Pageable pageable);
}
