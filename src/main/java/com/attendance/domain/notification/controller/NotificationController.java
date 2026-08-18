package com.attendance.domain.notification.controller;

import com.attendance.domain.notification.dto.NotificationRequest;
import com.attendance.domain.notification.dto.NotificationResponse;
import com.attendance.domain.notification.entity.NotificationStatus;
import com.attendance.domain.notification.service.NotificationService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
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

/** 알림 관리 API 제공 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  /** 알림 생성(ADMIN 전용). scheduledAt이 없거나 지난 시각이면 생성 즉시 발송을 시도하고, 미래 시각이면 SCHEDULED로 남는다. */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<NotificationResponse>> createNotification(
      @Valid @RequestBody NotificationRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    NotificationResponse response =
        notificationService.createNotification(request, userDetails.getUserId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "알림이 생성되었습니다"));
  }

  /** 알림 목록 조회. ADMIN은 전체 알림을 status 필터(선택)로 조회하고, STUDENT는 본인 그룹(또는 전체발송) 대상의 발송완료 알림만 조회하며 이때 status 파라미터는 무시된다. */
  @GetMapping
  public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(required = false) NotificationStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<NotificationResponse> response =
        notificationService.getNotifications(
            userDetails.getRole(), userDetails.getUser().getGroupName(), status, pageable);
    return ResponseEntity.ok(ApiResponse.success(response, "알림 목록 조회 성공"));
  }

  /** 알림 취소(ADMIN 전용). 아직 발송 전(SCHEDULED)인 알림만 취소할 수 있다. */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{notificationId}")
  public ResponseEntity<ApiResponse<Void>> cancelNotification(@PathVariable Long notificationId) {
    notificationService.cancelNotification(notificationId);
    return ResponseEntity.ok(ApiResponse.success("알림이 취소되었습니다"));
  }
}
