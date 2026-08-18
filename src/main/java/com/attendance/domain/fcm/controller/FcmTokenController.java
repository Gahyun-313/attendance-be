package com.attendance.domain.fcm.controller;

import com.attendance.domain.fcm.dto.FcmTokenDeleteRequest;
import com.attendance.domain.fcm.dto.FcmTokenRequest;
import com.attendance.domain.fcm.dto.FcmTokenResponse;
import com.attendance.domain.fcm.service.FcmTokenService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * FCM 토큰 관리 API 제공 로그인만 하면 STUDENT/ADMIN 구분 없이 누구나 호출할 수 있다(SecurityConfig에 따로 등록하지 않아도
 * anyRequest().authenticated()로 "로그인 필요"가 자동 적용된다). userId는 위조를 막기 위해 요청 바디로 받지 않고 JWT 인증
 * 정보(@AuthenticationPrincipal)에서 꺼낸다.
 */
@RestController
@RequestMapping("/api/fcm/tokens")
@RequiredArgsConstructor
public class FcmTokenController {

  private final FcmTokenService fcmTokenService;

  /** FCM 토큰 등록(앱 최초 실행 시 호출) */
  @PostMapping
  public ResponseEntity<ApiResponse<FcmTokenResponse>> registerToken(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody FcmTokenRequest request) {
    FcmTokenResponse response = fcmTokenService.registerToken(userDetails.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "FCM 토큰이 등록되었습니다"));
  }

  /** FCM 토큰 삭제(로그아웃 시 호출). 본인 소유 토큰만 삭제할 수 있다. */
  @DeleteMapping
  public ResponseEntity<ApiResponse<Void>> deleteToken(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody FcmTokenDeleteRequest request) {
    fcmTokenService.deleteToken(userDetails.getUserId(), request.getToken());
    return ResponseEntity.ok(ApiResponse.success("FCM 토큰이 삭제되었습니다"));
  }
}
