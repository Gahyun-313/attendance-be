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

/** 출석 기록 Repository */
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

  /**
   * 중복 출석 방지 - WAITING 사전 등록 레코드 생성 전 동일 user+session 레코드 존재 여부 확인.
   * (DB의 user_id+session_id Unique 제약과 함께 애플리케이션 레벨 1차 방어)
   */
  boolean existsByUserIdAndSessionId(Long userId, Long sessionId);

  /**
   * user+session으로 기존 출석 레코드 조회 - 체크인 시 사용.
   * WAITING 상태로 사전 등록된 레코드가 있으면 그걸 갱신하고, 없으면 신규 생성한다 (AttendanceService.checkIn 참고).
   */
  Optional<AttendanceRecord> findByUserIdAndSessionId(Long userId, Long sessionId);

  /**
   * 내 출석 기록 조회 (페이징) - 앱 "내역" 화면용.
   * 정렬은 Controller에서 Pageable(checkInTime DESC 등)로 전달.
   * AttendanceRecord의 (user_id, check_in_time) 복합 인덱스로 필터링+정렬을 함께 커버한다.
   * cursor 기반 페이지네이션은 도입하지 않기로 결정 - 한 사용자의 출석 기록은 학기/그룹 단위로 자연히
   * 개수가 제한적이라(관리자 쪽 무한 스크롤성 대량 데이터와 다름), offset pagination으로도 충분하다고 판단.
   */
  Page<AttendanceRecord> findByUserId(Long userId, Pageable pageable);

  /** 세션별 출석 현황 조회 (관리자) - 한 세션의 전체 출석 레코드 */
  List<AttendanceRecord> findBySessionId(Long sessionId);

  /**
   * 세션별 + 특정 상태의 출석 레코드 목록 조회 - 세션 종료 시 남은 WAITING 레코드를 찾아 ABSENT로 일괄 처리할 때 사용
   * (AttendanceService.markAbsentForRemainingWaiting)
   */
  List<AttendanceRecord> findBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

  /** 세션별 전체 출석 레코드 수 - 대시보드 집계용 */
  long countBySessionId(Long sessionId);

  /** 세션별 특정 상태 레코드 수 - 대시보드(출석/지각/결석/대기 수) 집계용 */
  long countBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

  /** 전체 상태별 레코드 수 - 전체 통계(overall)의 누적 출석/지각/결석/대기 건수 집계용 */
  long countByStatus(AttendanceStatus status);

  /**
   * 단체별 + 상태별 레코드 수 - 전체 통계(overall)를 단체 범위로 한정할 때 사용 (위 countByStatus의 단체 격리 버전)
   * - AttendanceRecord는 organizationId 컬럼을 직접 갖고 있지 않아(세션을 통해 간접적으로만 소속이 결정됨),
   *   sessionId가 해당 단체 소속 세션 ID 목록에 포함되는지로 서브쿼리를 걸어 판단한다.
   */
  @Query(
          "SELECT COUNT(a) FROM AttendanceRecord a "
                  + "WHERE a.status = :status "
                  + "AND a.sessionId IN (SELECT s.id FROM AttendanceSession s WHERE s.organizationId = :organizationId)")
  long countByStatusAndOrganizationId(
          @Param("status") AttendanceStatus status, @Param("organizationId") Long organizationId);

  /** 사용자별 전체 출석 레코드 수 - 사용자 통계(user/me)의 총 참여 건수 집계용 */
  long countByUserId(Long userId);

  /** 사용자별 특정 상태 레코드 수 - 사용자 통계의 출석/지각/결석 건수 집계용 */
  long countByUserIdAndStatus(Long userId, AttendanceStatus status);
}