package com.attendance.domain.statistics.service;

import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.attendance.service.AttendanceService;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.statistics.dto.DashboardStatisticsResponse;
import com.attendance.domain.statistics.dto.GroupAttendanceRate;
import com.attendance.domain.statistics.dto.OverallStatisticsResponse;
import com.attendance.domain.statistics.dto.UserStatisticsResponse;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedisConfig;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 비즈니스 로직 - 세션별 통계는 별도로 만들지 않고 기존 AttendanceService.getSessionDashboard()(GET
 * /api/attendances/sessions/{sessionId}/dashboard)를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

  private static final int RECENT_SESSION_LIMIT = 5;
  private static final int RECENT_RECORD_LIMIT = 5;

  private final AttendanceRepository attendanceRepository;
  private final SessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final AttendanceService attendanceService;

  /**
   * 전체 통계 (ADMIN) - 시스템 전체 누적 수치 스냅샷 GET /api/statistics/overall - count 쿼리 4번을 매번 다시 실행하는 대신 1분간
   * 캐싱(RedisConfig). organizationId가 유일한 파라미터라 Spring의 기본 캐시 키 생성기가 이 값을 그대로 키로 써서 단체별로 캐시 항목이
   * 분리된다(다른 단체 관리자에게 캐시된 값이 잘못 섞여 나가는 문제 없음) - sessionDashboard처럼 수동 무효화는 하지 않음 - 체크인/상태변경마다 여기까지
   * 지우러 다니면 손댈 곳이 너무 많아지고, "최대 1분 지연"은 전체 누적 스냅샷 성격상 감수할 만한 오차라고 판단했다 (RedisConfig 주석 참고).
   */
  @Cacheable(cacheNames = RedisConfig.CACHE_OVERALL_STATISTICS)
  public OverallStatisticsResponse getOverallStatistics(Long organizationId) {
    long totalStudents =
        userRepository.countByRoleAndOrganizationId(UserRole.STUDENT, organizationId);
    long totalSessions = sessionRepository.countByOrganizationId(organizationId);
    long totalCompletedSessions =
        sessionRepository.countByOrganizationIdAndStatus(organizationId, SessionStatus.COMPLETED);
    long present =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.PRESENT, organizationId);
    long late =
        attendanceRepository.countByStatusAndOrganizationId(AttendanceStatus.LATE, organizationId);
    long absent =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.ABSENT, organizationId);
    long waiting =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.WAITING, organizationId);

    return OverallStatisticsResponse.of(
        totalStudents, totalSessions, totalCompletedSessions, present, late, absent, waiting);
  }

  /**
   * 대시보드 통계 (ADMIN) - 오늘/최근/그룹별 관점의 요약·트렌드 GET /api/statistics/dashboard -
   * calculateGroupAttendanceRates()가 그룹 수 x 세션 수만큼 count 쿼리를 반복 실행하는 가장 무거운 조회라 캐싱 효과가 가장 큰 지점.
   * overall과 같은 이유로 1분 TTL만 적용하고 별도 무효화는 하지 않는다. (organizationId가 캐시 키에 포함되는 원리도 overall과 동일)
   */
  @Cacheable(cacheNames = RedisConfig.CACHE_DASHBOARD_STATISTICS)
  public DashboardStatisticsResponse getDashboardStatistics(Long organizationId) {
    long todaySessionCount =
        sessionRepository.countByOrganizationIdAndSessionDate(organizationId, LocalDate.now());
    long activeSessionCount =
        sessionRepository.countByOrganizationIdAndStatus(organizationId, SessionStatus.ACTIVE);
    double recentAttendanceRate = calculateRecentAttendanceRate(organizationId);
    List<GroupAttendanceRate> groupAttendanceRates = calculateGroupAttendanceRates(organizationId);

    return DashboardStatisticsResponse.builder()
        .todaySessionCount(todaySessionCount)
        .activeSessionCount(activeSessionCount)
        .recentAttendanceRate(recentAttendanceRate)
        .groupAttendanceRates(groupAttendanceRates)
        .build();
  }

  /**
   * 사용자별 통계 (ADMIN) / 내 통계 (본인) 공용 로직 GET /api/statistics/users/{userId}, GET /api/statistics/me -
   * 다른 단체 사용자의 통계를 조회하려 하면 존재 자체를 노출하지 않기 위해 404로 처리 (getUser와 동일한 패턴)
   */
  public UserStatisticsResponse getUserStatistics(Long userId, Long organizationId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    if (!user.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.USER_NOT_FOUND);
    }

    long total = attendanceRepository.countByUserId(userId);
    long present = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.PRESENT);
    long late = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.LATE);
    long absent = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.ABSENT);

    List<AttendanceResponse> recentRecords =
        attendanceRepository
            .findByUserId(
                userId,
                PageRequest.of(0, RECENT_RECORD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, user))
            .getContent();

    return UserStatisticsResponse.of(
        userId, user.getName(), user.getGroupName(), total, present, late, absent, recentRecords);
  }

  // ------------------------------------------------
  // 내부 유틸
  // ------------------------------------------------

  /** 최근 완료된 세션 N개의 평균 출석률 - 기존 AttendanceService.getSessionDashboard()를 재사용해 값을 얻는다 */
  private double calculateRecentAttendanceRate(Long organizationId) {
    List<AttendanceSession> recentSessions =
        sessionRepository.findByOrganizationIdAndStatusOrderBySessionDateDesc(
            organizationId, SessionStatus.COMPLETED, PageRequest.of(0, RECENT_SESSION_LIMIT));

    if (recentSessions.isEmpty()) {
      return 0.0;
    }

    double average =
        recentSessions.stream()
            .mapToDouble(
                session ->
                    attendanceService.getSessionDashboard(session.getId()).getAttendanceRate())
            .average()
            .orElse(0.0);
    return Math.round(average * 10.0) / 10.0;
  }

  /** 그룹별 누적 출석 현황 (완료된 세션 기준) 계산 - organizationId로 단체 범위 한정 */
  private List<GroupAttendanceRate> calculateGroupAttendanceRates(Long organizationId) {
    List<String> groupNames =
        userRepository.findDistinctGroupNames(UserRole.STUDENT, organizationId);

    return groupNames.stream()
        .map(
            groupName -> {
              long targetCount =
                  userRepository.countByRoleAndGroupNameAndOrganizationId(
                      UserRole.STUDENT, groupName, organizationId);
              List<AttendanceSession> completedSessions =
                  sessionRepository.findByOrganizationIdAndGroupNameAndStatus(
                      organizationId, groupName, SessionStatus.COMPLETED);

              long present = 0;
              long late = 0;
              long absent = 0;
              for (AttendanceSession session : completedSessions) {
                present +=
                    attendanceRepository.countBySessionIdAndStatus(
                        session.getId(), AttendanceStatus.PRESENT);
                late +=
                    attendanceRepository.countBySessionIdAndStatus(
                        session.getId(), AttendanceStatus.LATE);
                absent +=
                    attendanceRepository.countBySessionIdAndStatus(
                        session.getId(), AttendanceStatus.ABSENT);
              }

              return GroupAttendanceRate.of(groupName, targetCount, present, late, absent);
            })
        .toList();
  }
}
