package com.attendance.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소셜 로그인 요청 DTO - POST /api/auth/oauth/{provider} */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginRequest {

    // 구글: ID Token(JWT), 카카오: Access Token - 프론트가 각 제공자 SDK로 발급받아 그대로 전달
    @NotBlank(message = "토큰은 필수입니다")
    private String token;

    // 최초 로그인(신규 조인)일 때만 필수 - 이미 연결된 계정이면 없어도 됨
    private String organizationCode;
}