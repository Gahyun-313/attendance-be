package com.attendance.domain.session.controller;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.dto.SessionRequest;
import com.attendance.domain.session.dto.SessionResponse;
import com.attendance.domain.session.service.SessionService;
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
import org.springframework.web.bind.annotation.*;

/** 출석 세션 관리 API Controller */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;

  /** 세션 생성 POST /api/sessions - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<SessionResponse>> createSession(
      @Valid @RequestBody SessionRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    SessionResponse response =
        sessionService.createSession(
            request, userDetails.getUserId(), userDetails.getOrganizationId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "세션이 생성되었습니다"));
  }

  /** 세션 목록 조회 GET /api/sessions - status: 상태별 필터링 (선택) - keyword: 세션명 검색 (선택) */
  @GetMapping
  public ResponseEntity<ApiResponse<Page<SessionResponse>>> getSessions(
      @RequestParam(required = false) SessionStatus status,
      @RequestParam(required = false) String keyword,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20, sort = "sessionDate", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<SessionResponse> response =
        sessionService.getSessions(status, keyword, userDetails.getOrganizationId(), pageable);
    return ResponseEntity.ok(ApiResponse.success(response, "세션 목록 조회 성공"));
  }

  /** 세션 상세 조회 GET /api/sessions/{sessionId} */
  @GetMapping("/{sessionId}")
  public ResponseEntity<ApiResponse<SessionResponse>> getSession(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    SessionResponse response =
        sessionService.getSession(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "세션 조회 성공"));
  }

  /** 세션 수정 PUT /api/sessions/{sessionId} - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{sessionId}")
  public ResponseEntity<ApiResponse<SessionResponse>> updateSession(
      @PathVariable Long sessionId,
      @Valid @RequestBody SessionRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    SessionResponse response =
        sessionService.updateSession(sessionId, request, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "세션이 수정되었습니다"));
  }

  /** 세션 삭제 DELETE /api/sessions/{sessionId} - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{sessionId}")
  public ResponseEntity<ApiResponse<Void>> deleteSession(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    sessionService.deleteSession(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success("세션이 삭제되었습니다"));
  }

  /** 세션 시작 POST /api/sessions/{sessionId}/start - ADMIN 전용, SCHEDULED → ACTIVE */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{sessionId}/start")
  public ResponseEntity<ApiResponse<SessionResponse>> startSession(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    SessionResponse response =
        sessionService.startSession(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "세션이 시작되었습니다"));
  }

  /** 세션 종료 POST /api/sessions/{sessionId}/close - ADMIN 전용, ACTIVE → COMPLETED */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{sessionId}/close")
  public ResponseEntity<ApiResponse<SessionResponse>> closeSession(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    SessionResponse response =
        sessionService.closeSession(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "세션이 종료되었습니다"));
  }

  /** 세션 취소 POST /api/sessions/{sessionId}/cancel - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{sessionId}/cancel")
  public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(
      @PathVariable Long sessionId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    SessionResponse response =
        sessionService.cancelSession(sessionId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "세션이 취소되었습니다"));
  }

  /** 활성 세션 조회 GET /api/sessions/active - 현재 진행 중인 세션 목록 */
  @GetMapping("/active")
  public ResponseEntity<ApiResponse<List<SessionResponse>>> getActiveSessions(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    List<SessionResponse> response =
        sessionService.getActiveSessions(userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "활성 세션 조회 성공"));
  }
}
