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

/** 인증 API Controller */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;

  /** 로그인 POST /api/auth/login */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response, "로그인에 성공했습니다"));
  }

  /** 토큰 갱신 POST /api/auth/refresh */
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthResponse>> refresh(
          @Valid @RequestBody TokenRefreshRequest request) {
    AuthResponse response = authService.refresh(request);
    return ResponseEntity.ok(ApiResponse.success(response, "토큰이 갱신되었습니다"));
  }

  /** 로그아웃 POST /api/auth/logout */
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
          @AuthenticationPrincipal CustomUserDetails userDetails) {
    authService.logout(userDetails.getUserId());
    return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다"));
  }

  /**
   * 소셜 로그인 (구글/카카오) POST /api/auth/oauth/{provider}
   * - provider 경로 변수: "google" 또는 "kakao" - 프론트가 각 제공자 SDK로 발급받은 토큰을 그대로 body로 전달한다. 기존
   * 연동 계정이면 로그인만, 없으면 organizationCode로 신규 ADMIN 계정을 만들며 조인한다.
   */
  @PostMapping("/oauth/{provider}")
  public ResponseEntity<ApiResponse<AuthResponse>> oauthLogin(
          @PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
    AuthResponse response = authService.oauthLogin(provider, request);
    return ResponseEntity.ok(ApiResponse.success(response, "로그인에 성공했습니다"));
  }

  /**
   * 이메일 인증 코드 발송 POST /api/auth/join/email/request
   * - 소셜 로그인이 차단된 환경(회사 네트워크 등)을 위한 대체 조인 경로 1단계 - 단체 코드 검증 + 이메일로 인증 코드 발송.
   */
  @PostMapping("/join/email/request")
  public ResponseEntity<ApiResponse<Void>> requestEmailJoin(
          @Valid @RequestBody EmailJoinRequest request) {
    emailVerificationService.sendVerificationCode(request.getOrganizationCode(), request.getEmail());
    return ResponseEntity.ok(ApiResponse.success("인증 코드를 발송했습니다"));
  }

  /**
   * 이메일 인증 코드 검증 + 계정 생성 POST /api/auth/join/email/verify
   * - 대체 조인 경로 2단계 - 코드 검증 성공 시 ADMIN 계정을 생성하고 즉시 로그인 처리(토큰 발급)한다.
   */
  @PostMapping("/join/email/verify")
  public ResponseEntity<ApiResponse<AuthResponse>> verifyEmailJoin(
          @Valid @RequestBody EmailJoinVerifyRequest request) {
    AuthResponse response = authService.joinByEmail(request);
    return ResponseEntity.ok(ApiResponse.success(response, "가입 및 로그인에 성공했습니다"));
  }

  /**
   * 비밀번호 재설정 인증 코드 발송 POST /api/auth/password-reset/request
   * - 로그아웃 상태(비밀번호를 잊은 사용자) 전용 - 이메일로 등록된 계정이 있어야 하고,
   * - 소셜 로그인은 대상이 아니다.
   */
  @PostMapping("/password-reset/request")
  public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
          @Valid @RequestBody PasswordResetRequest request) {
    emailVerificationService.sendPasswordResetCode(request.getEmail());
    return ResponseEntity.ok(ApiResponse.success("인증 코드를 발송했습니다"));
  }

  /**
   * 비밀번호 재설정 코드 검증 + 새 비밀번호 적용 POST /api/auth/password-reset/verify
   * - 성공해도 자동 로그인은 되지 않는다 - 새 비밀번호로 /api/auth/login을 다시 호출해야 한다.
   */
  @PostMapping("/password-reset/verify")
  public ResponseEntity<ApiResponse<Void>> PasswordResetVerifyRequest(
          @Valid @RequestBody PasswordResetVerifyRequest request) {
    authService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.success("비밀번호가 재설정되었습니다"));
  }
}