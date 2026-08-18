package com.attendance.domain.user.controller;

import com.attendance.domain.user.dto.*;
import com.attendance.domain.user.service.AuthService;
import com.attendance.domain.user.service.EmailVerificationService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API 제공 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;

  /** 로그인 처리 */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response, "로그인에 성공했습니다"));
  }

  /** 토큰 갱신 */
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @Valid @RequestBody TokenRefreshRequest request) {
    AuthResponse response = authService.refresh(request);
    return ResponseEntity.ok(ApiResponse.success(response, "토큰이 갱신되었습니다"));
  }

  /** 로그아웃 처리 */
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    authService.logout(userDetails.getUserId());
    return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다"));
  }

  /**
   * 소셜 로그인 처리(구글/카카오)
   * provider는 "google"/"kakao"이며, 프론트가 SDK로 받은 토큰을 그대로 전달한다.
   * 기존 연동 계정이면 로그인시키고, 없으면 organizationCode로 신규 ADMIN 계정을 만들며 조인시킨다.
   */
  @PostMapping("/oauth/{provider}")
  public ResponseEntity<ApiResponse<AuthResponse>> oauthLogin(
      @PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
    AuthResponse response = authService.oauthLogin(provider, request);
    return ResponseEntity.ok(ApiResponse.success(response, "로그인에 성공했습니다"));
  }

  /** 이메일 인증 코드 발송. 소셜 로그인이 막힌 환경의 대체 조인 경로 1단계로, 단체 코드를 검증한 뒤 이메일을 발송한다. */
  @PostMapping("/join/email/request")
  public ResponseEntity<ApiResponse<Void>> requestEmailJoin(
      @Valid @RequestBody EmailJoinRequest request) {
    emailVerificationService.sendVerificationCode(
        request.getOrganizationCode(), request.getEmail());
    return ResponseEntity.ok(ApiResponse.success("인증 코드를 발송했습니다"));
  }

  /** 이메일 인증 코드 검증 및 계정 생성. 대체 조인 경로 2단계로, 성공하면 ADMIN 계정을 생성한 뒤 바로 로그인 처리한다. */
  @PostMapping("/join/email/verify")
  public ResponseEntity<ApiResponse<AuthResponse>> verifyEmailJoin(
      @Valid @RequestBody EmailJoinVerifyRequest request) {
    AuthResponse response = authService.joinByEmail(request);
    return ResponseEntity.ok(ApiResponse.success(response, "가입 및 로그인에 성공했습니다"));
  }

  /** 비밀번호 재설정 인증 코드 발송. 로그아웃 상태(비밀번호 분실) 전용이며, 소셜 로그인 계정은 대상이 아니다. */
  @PostMapping("/password-reset/request")
  public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    emailVerificationService.sendPasswordResetCode(request.getEmail());
    return ResponseEntity.ok(ApiResponse.success("인증 코드를 발송했습니다"));
  }

  /** 비밀번호 재설정 코드 검증 및 새 비밀번호 적용. 성공해도 자동 로그인은 되지 않으며, 새 비밀번호로 다시 로그인해야 한다. */
  @PostMapping("/password-reset/verify")
  public ResponseEntity<ApiResponse<Void>> PasswordResetVerifyRequest(
      @Valid @RequestBody PasswordResetVerifyRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.success("비밀번호가 재설정되었습니다"));
  }
}
