package com.attendance.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 시간대별 체크인 건수 항목 - DashboardStatisticsResponse의 hourlyCheckInTrend 리스트 원소 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화용 기본 생성자
public class HourlyCheckInCount {

  private int hour; // 0~23
  private long count;
}
