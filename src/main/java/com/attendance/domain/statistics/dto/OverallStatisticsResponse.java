package com.attendance.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전체 통계 응답 DTO - GET /api/statistics/overall (ADMIN)
 * 시스템 전체 누적 수치 스냅샷. 대시보드(요약/트렌드)와 달리 시간 흐름 관계없이 지금까지의 총량을 보여준다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화용 기본 생성자 (AttendanceDashboardResponse와 동일한 이유)
public class OverallStatisticsResponse {

    private long totalStudents;           // 전체 학생 수
    private long totalSessions;           // 전체 세션 수 (상태 무관)
    private long totalCompletedSessions;  // 완료된 세션 수
    private long totalPresent;            // 전체 출석 건수 누적
    private long totalLate;               // 전체 지각 건수 누적
    private long totalAbsent;             // 전체 결석 건수 누적
    private long totalWaiting;            // 전체 대기(WAITING) 건수 누적 (진행 중인 세션의 미체크인 인원)
    private double overallAttendanceRate; // (전체 출석 + 지각) / (출석 + 지각 + 결석) * 100, WAITING은 분모 제외

    public static OverallStatisticsResponse of(
            long totalStudents,
            long totalSessions,
            long totalCompletedSessions,
            long present,
            long late,
            long absent,
            long waiting) {
        long base = present + late + absent; // 아직 결과가 확정 안 된 WAITING은 출석률 분모에서 제외
        double rate = base == 0 ? 0.0 : Math.round((present + late) * 1000.0 / base) / 10.0;
        return OverallStatisticsResponse.builder()
                .totalStudents(totalStudents)
                .totalSessions(totalSessions)
                .totalCompletedSessions(totalCompletedSessions)
                .totalPresent(present)
                .totalLate(late)
                .totalAbsent(absent)
                .totalWaiting(waiting)
                .overallAttendanceRate(rate)
                .build();
    }
}