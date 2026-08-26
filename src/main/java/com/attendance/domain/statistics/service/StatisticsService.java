package com.attendance.domain.statistics.service;

import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.attendance.service.AttendanceService;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.statistics.dto.*;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedisConfig;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 통계 비즈니스 로직 처리. 세션별 통계는 AttendanceService.getSessionDashboard()를 그대로 재사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

  private static final int RECENT_SESSION_LIMIT = 5;
  private static final int RECENT_RECORD_LIMIT = 5;
  private static final int RANKING_MIN_LIMIT = 1;
  private static final int RANKING_MAX_LIMIT = 50;
  private static final int TODAY_TREND_START_HOUR = 0;
  private static final int TODAY_TREND_END_HOUR = 23;
  // 오늘 출석 집계 대상 세션 상태. ACTIVE도 포함해 하루 중간에도 실시간으로 값이 갱신되게 한다.
  private static final List<SessionStatus> TODAY_SUMMARY_STATUSES =
      List.of(SessionStatus.ACTIVE, SessionStatus.COMPLETED);

  private final AttendanceRepository attendanceRepository;
  private final SessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final AttendanceService attendanceService;

  /**
   * 전체 통계 조회(ADMIN) 학생 수, 세션 수, 상태별 출석 건수를 시스템 전체 기준으로 집계 count 쿼리를 매번 실행하는 대신 organizationId 기준으로
   * 1분간 캐싱한다.
   */
  @Cacheable(cacheNames = RedisConfig.CACHE_OVERALL_STATISTICS)
  public OverallStatisticsResponse getOverallStatistics(Long organizationId) {
    // 학생 수, 세션 수 조회
    long totalStudents =
        userRepository.countByRoleAndOrganizationId(UserRole.STUDENT, organizationId);
    long totalSessions = sessionRepository.countByOrganizationId(organizationId);
    long totalCompletedSessions =
        sessionRepository.countByOrganizationIdAndStatus(organizationId, SessionStatus.COMPLETED);

    // 상태별 출석 건수 조회
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

    // 집계 결과를 응답 DTO로 변환
    return OverallStatisticsResponse.of(
        totalStudents, totalSessions, totalCompletedSessions, present, late, absent, waiting);
  }

  /**
   * 대시보드 통계 조회(ADMIN) 오늘 세션 수, 활성 세션 수, 최근 출석률, 그룹별 출석률을 집계 그룹별 집계가 그룹x세션 수만큼 쿼리를 반복하는 가장 무거운 조회라
   * 1분간 캐싱한다.
   */
  @Cacheable(cacheNames = RedisConfig.CACHE_DASHBOARD_STATISTICS)
  public DashboardStatisticsResponse getDashboardStatistics(Long organizationId) {
    // 오늘 세션 수, 활성 세션 수 조회
    long todaySessionCount =
        sessionRepository.countByOrganizationIdAndSessionDate(organizationId, LocalDate.now());
    long activeSessionCount =
        sessionRepository.countByOrganizationIdAndStatus(organizationId, SessionStatus.ACTIVE);

    // 최근 완료된 세션을 기준으로 평균 출석률 계산
    double recentAttendanceRate = calculateRecentAttendanceRate(organizationId);

    // 그룹별 누적 출석률 계산
    List<GroupAttendanceRate> groupAttendanceRates = calculateGroupAttendanceRates(organizationId);

    // 오늘 출석률/상태분포/시간대별 체크인 추이 집계 (원본 데이터가 같아 한 번에 묶어 계산)
    TodayAttendanceSummary todaySummary = calculateTodayAttendanceSummary(organizationId);

    // 집계 결과를 응답 DTO로 변환
    return DashboardStatisticsResponse.builder()
        .todaySessionCount(todaySessionCount)
        .activeSessionCount(activeSessionCount)
        .recentAttendanceRate(recentAttendanceRate)
        .groupAttendanceRates(groupAttendanceRates)
        .todayAttendanceRate(todaySummary.attendanceRate())
        .todayPresentCount(todaySummary.present())
        .todayLateCount(todaySummary.late())
        .todayAbsentCount(todaySummary.absent())
        .todayWaitingCount(todaySummary.waiting())
        .hourlyCheckInTrend(todaySummary.hourlyCheckInTrend())
        .build();
  }

  /** 출석률 상/하위 랭킹 조회(ADMIN) 학생별 누적 출석률을 계산해 상위/하위 N명을 추출 학생 수만큼 쿼리를 반복하는 무거운 집계라 1분간 캐싱한다. */
  @Cacheable(cacheNames = RedisConfig.CACHE_ATTENDACNE_RANKING)
  public AttendanceRankingResponse getAttendanceRanking(Long organizationId, int limit) {
    int safeLimit = Math.max(RANKING_MIN_LIMIT, Math.min(limit, RANKING_MAX_LIMIT));

    // 단체에 속한 학생 목록 조회
    List<User> students =
        userRepository.findByRoleAndOrganizationId(UserRole.STUDENT, organizationId);

    // 학생별 출석 기록을 집계해 랭킹 데이터로 변환
    // 출석 기록이 없는 학생은 랭킹에서 제외한다.
    List<UserAttendanceRanking> rankings =
        students.stream().map(this::toRankingOrNull).filter(Objects::nonNull).toList();

    // 출석률이 높은 순으로 정렬해 상위 N명 추출
    List<UserAttendanceRanking> topRanking =
        rankings.stream()
            .sorted(Comparator.comparingDouble(UserAttendanceRanking::getAttendanceRate).reversed())
            .limit(safeLimit)
            .toList();

    // 출석률이 낮은 순으로 정렬해 하위 N명 추출
    List<UserAttendanceRanking> bottomRanking =
        rankings.stream()
            .sorted(Comparator.comparingDouble(UserAttendanceRanking::getAttendanceRate))
            .limit(safeLimit)
            .toList();

    return AttendanceRankingResponse.builder()
        .topRanking(topRanking)
        .bottomRanking(bottomRanking)
        .build();
  }

  /** 사용자별 통계(ADMIN)/내 통계(본인) 공용 로직 처리 */
  public UserStatisticsResponse getUserStatistics(Long userId, Long organizationId) {
    // 사용자 조회 및 소속 단체 일치 확인
    // 다른 단체의 데이터 존재 여부를 노출하지 않도록 단체가 다르면 동일하게 404를 반환한다.
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    if (!user.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.USER_NOT_FOUND);
    }

    // 상태별 출석 건수 조회
    long total = attendanceRepository.countByUserId(userId);
    long present = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.PRESENT);
    long late = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.LATE);
    long absent = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.ABSENT);

    // 최근 출석 이력 최대 5건 조회
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

  // 내부 유틸

  /**
   * 오늘(sessionDate=오늘) 출석률/상태분포/시간대별 체크인 추이 집계 세션마다 출석 레코드를 반복 조회하지만, 하루에 발생하는 세션 수가 적어 그룹별
   * 집계(calculateGroupAttendanceRates)와 동일한 방식을 그대로 따른다.
   */
  private TodayAttendanceSummary calculateTodayAttendanceSummary(Long organizationId) {
    // ACTIVE+COMPLETED 상태의 오늘 세션 조회. ACTIVE도 포함해 하루 중간에도 값이 실시간으로 갱신되게 한다.
    List<AttendanceSession> todaySessions =
        sessionRepository.findByOrganizationIdAndSessionDateAndStatusIn(
            organizationId, LocalDate.now(), TODAY_SUMMARY_STATUSES);

    // 0~23시(하루 전체) 구간을 0으로 초기화해, 체크인이 없는 시간대도 차트에 빈 구간 없이 표시되게 한다.
    Map<Integer, Long> hourlyCounts = new LinkedHashMap<>();
    for (int hour = TODAY_TREND_START_HOUR; hour <= TODAY_TREND_END_HOUR; hour++) {
      hourlyCounts.put(hour, 0L);
    }

    long targetCount = 0;
    long present = 0;
    long late = 0;
    long absent = 0;
    long waiting = 0;

    for (AttendanceSession session : todaySessions) {
      List<AttendanceRecord> records = attendanceRepository.findBySessionId(session.getId());

      // 세션별 대상자 수 누적. 그룹 미지정 세션은 실제 생성된 레코드 수로 근사한다(AttendanceDashboardResponse와 동일한 방식).
      targetCount +=
          session.getGroupName() != null
              ? userRepository.countByRoleAndGroupNameAndOrganizationId(
                  UserRole.STUDENT, session.getGroupName(), organizationId)
              : records.size();

      for (AttendanceRecord record : records) {
        switch (record.getStatus()) {
          case PRESENT -> present++;
          case LATE -> late++;
          case ABSENT -> absent++;
          case WAITING -> waiting++;
        }
        // 체크인 시각이 있는 기록만 시간대별 추이에 반영한다(WAITING/ABSENT는 checkInTime이 없음).
        if (record.getCheckInTime() != null) {
          hourlyCounts.computeIfPresent(
              record.getCheckInTime().getHour(), (hour, count) -> count + 1);
        }
      }
    }

    double rate =
        targetCount == 0 ? 0.0 : Math.round((present + late) * 1000.0 / targetCount) / 10.0;
    List<HourlyCheckInCount> hourlyTrend =
        hourlyCounts.entrySet().stream()
            .map(
                entry ->
                    HourlyCheckInCount.builder()
                        .hour(entry.getKey())
                        .count(entry.getValue())
                        .build())
            .toList();

    return new TodayAttendanceSummary(rate, present, late, absent, waiting, hourlyTrend);
  }

  /** calculateTodayAttendanceSummary 계산 결과를 담는 내부 전용 집계 홀더 */
  private record TodayAttendanceSummary(
      double attendanceRate,
      long present,
      long late,
      long absent,
      long waiting,
      List<HourlyCheckInCount> hourlyCheckInTrend) {}

  /** 최근 완료된 세션 N개의 평균 출석률 계산 */
  private double calculateRecentAttendanceRate(Long organizationId) {
    // 최근 완료된 세션 조회. 최대 5개까지만 가져온다.
    List<AttendanceSession> recentSessions =
        sessionRepository.findByOrganizationIdAndStatusOrderBySessionDateDesc(
            organizationId, SessionStatus.COMPLETED, PageRequest.of(0, RECENT_SESSION_LIMIT));

    if (recentSessions.isEmpty()) {
      return 0.0;
    }

    // 세션별 출석률 조회 후 평균 계산. AttendanceService.getSessionDashboard()를 재사용한다.
    double average =
        recentSessions.stream()
            .mapToDouble(
                session ->
                    attendanceService
                        .getSessionDashboard(session.getId(), session.getOrganizationId())
                        .getAttendanceRate())
            .average()
            .orElse(0.0);
    return Math.round(average * 10.0) / 10.0;
  }

  /** 그룹별 누적 출석 현황(완료된 세션 기준) 계산 */
  private List<GroupAttendanceRate> calculateGroupAttendanceRates(Long organizationId) {
    // 단체에 존재하는 학생 그룹 목록 조회
    List<String> groupNames =
        userRepository.findDistinctGroupNames(UserRole.STUDENT, organizationId);

    return groupNames.stream()
        .map(
            groupName -> {
              // 해당 그룹의 전체 학생 수 조회
              long targetCount =
                  userRepository.countByRoleAndGroupNameAndOrganizationId(
                      UserRole.STUDENT, groupName, organizationId);

              // 그룹에 속한 완료된 세션 조회
              List<AttendanceSession> completedSessions =
                  sessionRepository.findByOrganizationIdAndGroupNameAndStatus(
                      organizationId, groupName, SessionStatus.COMPLETED);

              // 완료된 세션의 출석 상태 누적 집계
              long present = 0;
              long late = 0;
              long absent = 0;
              for (AttendanceSession session : completedSessions) {
                // 세션별 출석 상태를 집계해 그룹 누적 값에 더함
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

              // 그룹별 집계 결과를 응답 객체로 변환
              return GroupAttendanceRate.of(groupName, targetCount, present, late, absent);
            })
        .toList();
  }

  /** 학생 1명의 출석 기록을 집계해 랭킹 항목으로 변환. 기록이 하나도 없으면 null 반환(호출부에서 필터링). */
  private UserAttendanceRanking toRankingOrNull(User student) {
    // 학생의 상태별 출석 건수 조회
    long present =
        attendanceRepository.countByUserIdAndStatus(student.getId(), AttendanceStatus.PRESENT);
    long late = attendanceRepository.countByUserIdAndStatus(student.getId(), AttendanceStatus.LATE);
    long absent =
        attendanceRepository.countByUserIdAndStatus(student.getId(), AttendanceStatus.ABSENT);
    if (present + late + absent == 0) {
      return null;
    }
    return UserAttendanceRanking.of(
        student.getId(), student.getName(), student.getGroupName(), present, late, absent);
  }
}
