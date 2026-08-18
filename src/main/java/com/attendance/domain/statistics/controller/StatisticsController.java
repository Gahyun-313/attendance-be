package com.attendance.domain.statistics.controller;

import com.attendance.domain.statistics.dto.AttendanceRankingResponse;
import com.attendance.domain.statistics.dto.DashboardStatisticsResponse;
import com.attendance.domain.statistics.dto.OverallStatisticsResponse;
import com.attendance.domain.statistics.dto.UserStatisticsResponse;
import com.attendance.domain.statistics.service.StatisticsService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 통계 API 제공. 세션별 통계는 별도 엔드포인트 없이 기존 GET /api/attendances/sessions/{sessionId}/dashboard를 사용한다. */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

  private final StatisticsService statisticsService;

  /** 전체 통계 조회(ADMIN 전용). 시스템 전체 누적 수치(학생 수, 세션 수, 상태별 출석 건수, 전체 출석률)를 반환한다. */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/overall")
  public ResponseEntity<ApiResponse<OverallStatisticsResponse>> getOverallStatistics(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    OverallStatisticsResponse response =
        statisticsService.getOverallStatistics(userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "전체 통계 조회 성공"));
  }

  /** 대시보드 통계 조회(ADMIN 전용). 오늘/최근/그룹별 관점의 요약과 트렌드를 반환한다. */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/dashboard")
  public ResponseEntity<ApiResponse<DashboardStatisticsResponse>> getDashboardStatistics(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    DashboardStatisticsResponse response =
        statisticsService.getDashboardStatistics(userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "대시보드 통계 조회 성공"));
  }

  /** 출석률 상/하위 랭킹 조회(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/ranking")
  // limit: 상/하위 몇 명씩 보여줄지 지정한다(기본값 5, 1~50으로 clamp된다).
  public ResponseEntity<ApiResponse<AttendanceRankingResponse>> getAttendanceRanking(
      @RequestParam(defaultValue = "5") int limit,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    AttendanceRankingResponse response =
        statisticsService.getAttendanceRanking(userDetails.getOrganizationId(), limit);
    return ResponseEntity.ok(ApiResponse.success(response, "출석률 랭킹 조회 성공"));
  }

  /** 특정 사용자의 통계 조회(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/users/{userId}")
  public ResponseEntity<ApiResponse<UserStatisticsResponse>> getUserStatistics(
      @PathVariable Long userId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserStatisticsResponse response =
        statisticsService.getUserStatistics(userId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "사용자 통계 조회 성공"));
  }

  /** 내 통계 조회(본인 전용, STUDENT/ADMIN 공통) */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserStatisticsResponse>> getMyStatistics(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserStatisticsResponse response =
        statisticsService.getUserStatistics(
            userDetails.getUserId(), userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "내 통계 조회 성공"));
  }
}
