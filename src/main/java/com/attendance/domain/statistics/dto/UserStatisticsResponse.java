package com.attendance.domain.statistics.dto;

import com.attendance.domain.attendance.dto.AttendanceResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사용자별 통계 응답 DTO - GET /api/statistics/users/{userId} (ADMIN), GET /api/statistics/me (본인) 두 엔드포인트가
 * 조회 주체(대상 userId)만 다르고 응답 구조/산출 로직은 동일하게 공유한다.
 */
@Getter
@Builder
@AllArgsConstructor
public class UserStatisticsResponse {

  private Long userId;
  private String name;
  private String groupName;
  private long totalRecords; // 총 참여(레코드 생성) 건수
  private long presentCount;
  private long lateCount;
  private long absentCount;
  private double attendanceRate; // (출석+지각)/(출석+지각+결석) * 100
  private List<AttendanceResponse> recentRecords; // 최근 출석 이력 (최대 5건)

  public static UserStatisticsResponse of(
      Long userId,
      String name,
      String groupName,
      long total,
      long present,
      long late,
      long absent,
      List<AttendanceResponse> recentRecords) {
    long base = present + late + absent;
    double rate = base == 0 ? 0.0 : Math.round((present + late) * 1000.0 / base) / 10.0;
    return UserStatisticsResponse.builder()
        .userId(userId)
        .name(name)
        .groupName(groupName)
        .totalRecords(total)
        .presentCount(present)
        .lateCount(late)
        .absentCount(absent)
        .attendanceRate(rate)
        .recentRecords(recentRecords)
        .build();
  }
}
