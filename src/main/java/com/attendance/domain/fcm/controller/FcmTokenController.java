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
 * FCM 토큰 관리 API
 * - 로그인한 사용자라면 STUDENT/ADMIN 구분 없이 누구나 호출 가능.
 *   SecurityConfig에 이 경로를 따로 등록하지 않아도 anyRequest().authenticated() 규칙에 걸려
 *   "로그인 필요"만 자동으로 적용된다.
 * - userId는 요청 바디로 받지 않는다. 로그인 시 발급한 JWT를 서버가 해석해 만든 인증 정보(@AuthenticationPrincipal)에서
 *   꺼내 쓴다. 요청 바디에 userId를 그대로 받으면 클라이언트가 남의 ID를 넣어 위조할 수 있기 때문.
 */
@RestController
@RequestMapping("/api/fcm/tokens")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    /** FCM 토큰 등록 POST /api/fcm/tokens - 앱 최초 실행 시 호출 */
    @PostMapping
    public ResponseEntity<ApiResponse<FcmTokenResponse>> registerToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FcmTokenRequest request) {
        FcmTokenResponse response = fcmTokenService.registerToken(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "FCM 토큰이 등록되었습니다"));
    }

    /** FCM 토큰 삭제 DELETE /api/fcm/tokens - 로그아웃 시 호출, 본인 소유 토큰만 삭제 가능 */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FcmTokenDeleteRequest request) {
        fcmTokenService.deleteToken(userDetails.getUserId(), request.getToken());
        return ResponseEntity.ok(ApiResponse.success("FCM 토큰이 삭제되었습니다"));
    }
}
