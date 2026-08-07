package com.attendance.domain.statistics.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출석률 상/하위 랭킹 응답 DTO - GET /api/statistics/ranking (ADMIN) - 출석 기록이 하나도 없는 학생(base=0)은 순위를 매길 근거가
 * 없어 topRanking/bottomRanking 양쪽 모두에서 제외.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화용 기본 생성자
public class AttendanceRankingResponse {

  private List<UserAttendanceRanking> topRanking; // 출석률 높은 순
  private List<UserAttendanceRanking> bottomRanking; // 출석률 낮은 순
}
