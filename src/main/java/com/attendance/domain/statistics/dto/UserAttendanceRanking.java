package com.attendance.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석률 랭킹 항목 AttendanceRankingResponse의 topRanking/bottomRanking 리스트 원소 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화용 기본 생성자 (AttendanceRankingResponse에 리스트로 중첩되어 캐싱되므로 이 DTO도 필요)
public class UserAttendanceRanking {
  private Long userId;
  private String name;
  private String groupName;
  private double attendanceRate; // (출석+지각)/(출석+지각+결석) * 100
  private long totalRecords; // 집계에 사용된 총 출석 레코드 수 (출석+지각+결석)

  public static UserAttendanceRanking of(
      Long userId, String name, String groupName, long present, long late, long absent) {
    long base = present + late + absent;
    double rate = base == 0 ? 0.0 : Math.round((present + late) * 1000.0 / base) / 10.0;
    return UserAttendanceRanking.builder()
        .userId(userId)
        .name(name)
        .groupName(groupName)
        .attendanceRate(rate)
        .totalRecords(base)
        .build();
  }
}
