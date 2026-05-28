package com.attendance.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 토큰 갱신 요청 DTO - Access Token이 만료되었을 때 Refresh Token으로 새 Access Token을 발급하기 위한 요청 객체 */
@Getter
@NoArgsConstructor // JSON 역직렬화를 위한 기본 생성자
@AllArgsConstructor // 테스트 등을 위한 전체 생성자
public class TokenRefreshRequest {

  // Refresh Token
  @NotBlank(message = "Refresh Token은 필수입니다")
  private String refreshToken;
}
