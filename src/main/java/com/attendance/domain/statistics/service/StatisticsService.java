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
 * 통계 비즈니스 로직 - 세션별 통계는 별도로 만들지 않고 기존
 * AttendanceService.getSessionDashboard()(GET /api/attendances/sessions/{sessionId}/dashboard)를
 * 그대로 재사용한다.
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
     * 전체 통계 (ADMIN) - 시스템 전체 누적 수치 스냅샷
     * GET /api/statistics/overall
     * - count 쿼리 4번을 매번 다시 실행하는 대신 1분간 캐싱(RedisConfig). 파라미터가 없는 메서드라 캐시 항목이 딱 하나뿐이고,
     *   sessionDashboard처럼 수동 무효화는 하지 않음 - 체크인/상태변경마다 여기까지 지우러 다니면 손댈 곳이 너무 많아지고,
     *   "최대 1분 지연"은 전체 누적 스냅샷 성격상 감수할 만한 오차라고 판단했다 (RedisConfig 주석 참고).
     */
    @Cacheable(cacheNames = RedisConfig.CACHE_OVERALL_STATISTICS)
    public OverallStatisticsResponse getOverallStatistics() {
        long totalStudents = userRepository.countByRole(UserRole.STUDENT);
        long totalSessions = sessionRepository.count();
        long totalCompletedSessions = sessionRepository.countByStatus(SessionStatus.COMPLETED);
        long present = attendanceRepository.countByStatus(AttendanceStatus.PRESENT);
        long late = attendanceRepository.countByStatus(AttendanceStatus.LATE);
        long absent = attendanceRepository.countByStatus(AttendanceStatus.ABSENT);
        long waiting = attendanceRepository.countByStatus(AttendanceStatus.WAITING);

        return OverallStatisticsResponse.of(
                totalStudents, totalSessions, totalCompletedSessions, present, late, absent, waiting);
    }

    /**
     * 대시보드 통계 (ADMIN) - 오늘/최근/그룹별 관점의 요약·트렌드
     * GET /api/statistics/dashboard
     * - calculateGroupAttendanceRates()가 그룹 수 x 세션 수만큼 count 쿼리를 반복 실행하는 가장 무거운 조회라
     *   캐싱 효과가 가장 큰 지점. overall과 같은 이유로 1분 TTL만 적용하고 별도 무효화는 하지 않는다.
     */
    @Cacheable(cacheNames = RedisConfig.CACHE_DASHBOARD_STATISTICS)
    public DashboardStatisticsResponse getDashboardStatistics() {
        long todaySessionCount = sessionRepository.countBySessionDate(LocalDate.now());
        long activeSessionCount = sessionRepository.countByStatus(SessionStatus.ACTIVE);
        double recentAttendanceRate = calculateRecentAttendanceRate();
        List<GroupAttendanceRate> groupAttendanceRates = calculateGroupAttendanceRates();

        return DashboardStatisticsResponse.builder()
                .todaySessionCount(todaySessionCount)
                .activeSessionCount(activeSessionCount)
                .recentAttendanceRate(recentAttendanceRate)
                .groupAttendanceRates(groupAttendanceRates)
                .build();
    }

    /**
     * 사용자별 통계 (ADMIN) / 내 통계 (본인) 공용 로직
     * GET /api/statistics/users/{userId}, GET /api/statistics/me
     */
    public UserStatisticsResponse getUserStatistics(Long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        long total = attendanceRepository.countByUserId(userId);
        long present = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.PRESENT);
        long late = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.LATE);
        long absent = attendanceRepository.countByUserIdAndStatus(userId, AttendanceStatus.ABSENT);

        List<AttendanceResponse> recentRecords =
                attendanceRepository
                        .findByUserId(
                                userId,
                                PageRequest.of(
                                        0, RECENT_RECORD_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, user))
                        .getContent();

        return UserStatisticsResponse.of(
                userId, user.getName(), user.getGroupName(), total, present, late, absent, recentRecords);
    }

    // ------------------------------------------------
    // 내부 유틸
    // ------------------------------------------------

    /** 최근 완료된 세션 N개의 평균 출석률 - 기존 AttendanceService.getSessionDashboard()를 재사용해 값을 얻는다 */
    private double calculateRecentAttendanceRate() {
        List<AttendanceSession> recentSessions =
                sessionRepository.findByStatusOrderBySessionDateDesc(
                        SessionStatus.COMPLETED, PageRequest.of(0, RECENT_SESSION_LIMIT));

        if (recentSessions.isEmpty()) {
            return 0.0;
        }

        double average =
                recentSessions.stream()
                        .mapToDouble(
                                session -> attendanceService.getSessionDashboard(session.getId()).getAttendanceRate())
                        .average()
                        .orElse(0.0);
        return Math.round(average * 10.0) / 10.0;
    }

    /** 그룹별 누적 출석 현황 (완료된 세션 기준) 계산 */
    private List<GroupAttendanceRate> calculateGroupAttendanceRates() {
        List<String> groupNames = userRepository.findDistinctGroupNames(UserRole.STUDENT);

        return groupNames.stream()
                .map(
                        groupName -> {
                            long targetCount =
                                    userRepository.countByRoleAndGroupName(UserRole.STUDENT, groupName);
                            List<AttendanceSession> completedSessions =
                                    sessionRepository.findByGroupNameAndStatus(
                                            groupName, SessionStatus.COMPLETED);

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