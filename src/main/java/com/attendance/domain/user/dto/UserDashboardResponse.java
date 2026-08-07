package com.attendance.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 대시보드 응답 DTO - GET /api/users/dashboard (ADMIN) 사용자 관리 화면 상단 요약 카드용. StatisticsService의
 * OverallStatisticsResponse와는 별개로 "사용자 목록" 화면에 특화된 4개 지표만 제공
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화용 기본 생성자
public class UserDashboardResponse {

  private long totalUsers; // 전체 사용자(STUDENT) 수
  private long activateUsers; // 활성 사용자 수 (activate = true)
  private double averageAttendanceRate; // 평균 출석률 (WAITING은 분모 제외)
  private long newUsersThisMonth; // 이번 달 신규 등록된 사용자 수

  public static UserDashboardResponse of(
      long totalUsers,
      long activeUsers,
      long present,
      long late,
      long absent,
      long newUsersThisMont) {
    long base = present + late + absent;
    double rate = base == 0 ? 0.0 : Math.round((present + late) * 1000.0 / base) / 10.0;
    return UserDashboardResponse.builder()
        .totalUsers(totalUsers)
        .activateUsers(activeUsers)
        .averageAttendanceRate(rate)
        .newUsersThisMonth(newUsersThisMont)
        .build();
  }
}
