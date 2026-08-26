package com.attendance.domain.statistics.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대시보드 통계 응답 DTO - GET /api/statistics/dashboard (ADMIN) 전체 통계(overall, 누적 수치)와 달리 "오늘/최근/그룹별" 관점의
 * 요약·트렌드 정보를 제공한다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화용 기본 생성자 (AttendanceDashboardResponse와 동일한 이유)
public class DashboardStatisticsResponse {

  private long todaySessionCount; // 오늘 날짜(sessionDate) 세션 수
  private long activeSessionCount; // 현재 ACTIVE 세션 수
  private double recentAttendanceRate; // 최근 완료된 세션 5개의 평균 출석률
  private List<GroupAttendanceRate> groupAttendanceRates; // 그룹별 누적 출석 현황 (완료된 세션 기준)

  // 오늘(sessionDate=오늘) 세션 기준 실시간 집계. ACTIVE 세션의 출석 데이터도 포함해 하루 중간에도 값이 갱신된다.
  private double todayAttendanceRate; // (오늘 출석+지각) / 오늘 세션 대상자 수 합계 * 100
  private long todayPresentCount;
  private long todayLateCount;
  private long todayAbsentCount;
  private long todayWaitingCount;
  private List<HourlyCheckInCount> hourlyCheckInTrend; // 오늘 0~23시(하루 전체) 시간대별 체크인 건수
}
