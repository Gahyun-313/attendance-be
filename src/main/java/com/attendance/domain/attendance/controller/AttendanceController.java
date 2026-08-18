package com.attendance.domain.attendance.controller;

import com.attendance.domain.attendance.dto.AttendanceDashboardResponse;
import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.dto.AttendanceStatusUpdateRequest;
import com.attendance.domain.attendance.dto.CheckInRequest;
import com.attendance.domain.attendance.service.AttendanceService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 출석 기록 관리 API 제공 */
@RestController
@RequestMapping("/api/attendances")
@RequiredArgsConstructor
public class AttendanceController {

  private final AttendanceService attendanceService;

  /** 출석 체크인 처리(STUDENT 전용). 요청 바디는 nfcTagUid만 받고, sessionId는 서버가 역추적한다. */
  @PreAuthorize("hasRole('STUDENT')")
  @PostMapping("/check-in")
  public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
      @Valid @RequestBody CheckInRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    AttendanceResponse response = attendanceService.checkIn(userDetails.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "출석이 완료되었습니다"));
  }

  /** 내 출석 기록 조회(STUDENT 전용). 기본 정렬은 체크인 시각 내림차순이며 offset 페이징을 사용한다. */
  @PreAuthorize("hasRole('STUDENT')")
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getMyAttendances(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20, sort = "checkInTime", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<AttendanceResponse> response =
        attendanceService.getMyAttendances(userDetails.getUserId(), pageable);
    return ResponseEntity.ok(ApiResponse.success(response, "내 출석 기록 조회 성공"));
  }

  /** 세션별 출석 현황 조회(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/sessions/{sessionId}")
  public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getSessionAttendances(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    List<AttendanceResponse> response =
        attendanceService.getSessionAttendances(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "세션별 출석 현황 조회 성공"));
  }

  /** 출석 상태 수정(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{attendanceId}/status")
  public ResponseEntity<ApiResponse<AttendanceResponse>> updateAttendanceStatus(
      @PathVariable Long attendanceId,
      @Valid @RequestBody AttendanceStatusUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    // modifiedBy는 요청 바디가 아니라 인증 주체(관리자 이름)에서 채워 위변조를 막는다.
    String modifiedBy = userDetails.getUser().getName();
    AttendanceResponse response =
        attendanceService.updateStatus(
            attendanceId, request, modifiedBy, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "출석 상태가 수정되었습니다"));
  }

  /** 출석 기록 삭제(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{attendanceId}")
  public ResponseEntity<ApiResponse<Void>> deleteAttendance(
      @PathVariable Long attendanceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    attendanceService.deleteAttendance(attendanceId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success("출석 기록이 삭제되었습니다"));
  }

  /** 세션별 출석 대시보드 조회(ADMIN 전용). 상태별 레코드 수와 출석률을 집계해 반환한다. */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/sessions/{sessionId}/dashboard")
  public ResponseEntity<ApiResponse<AttendanceDashboardResponse>> getSessionDashboard(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    AttendanceDashboardResponse response =
        attendanceService.getSessionDashboard(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "출석 대시보드 조회 성공"));
  }
}
