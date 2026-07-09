package com.attendance.domain.fcm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FCM 토큰 등록 요청 DTO - 앱이 최초 실행 시 발급받은 토큰을 서버로 보낼 때 사용 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenRequest {

  @NotBlank(message = "토큰은 필수입니다")
  @Size(max = 255, message = "토큰은 255자를 초과할 수 없습니다")
  private String token;

  // 선택값 - 안 보내면 엔티티에서 기본값 "AOS"로 채워짐 (FcmToken 생성자 참고)
  @Size(max = 20, message = "디바이스 타입은 20자를 초과할 수 없습니다")
  private String deviceType;
}
