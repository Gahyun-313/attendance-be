package com.attendance.domain.fcm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FCM 토큰 삭제 요청 DTO - 로그아웃 시 이 기기의 토큰을 지워달라고 보낼 때 사용 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenDeleteRequest {

  @NotBlank(message = "토큰은 필수입니다")
  private String token;
}
