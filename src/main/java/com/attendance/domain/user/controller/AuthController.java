package com.attendance.domain.user.controller;

import com.attendance.domain.user.dto.AuthResponse;
import com.attendance.domain.user.dto.LoginRequest;
import com.attendance.domain.user.dto.SignupRequest;
import com.attendance.domain.user.dto.TokenRefreshRequest;
import com.attendance.domain.user.dto.UserResponse;
import com.attendance.domain.user.service.AuthService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/** 인증 API Controller */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /** 회원가입 POST /api/auth/signup */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<UserResponse>> signup(
      @Valid @RequestBody SignupRequest request) {
    UserResponse response = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "회원가입이 완료되었습니다"));
  }

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
}
