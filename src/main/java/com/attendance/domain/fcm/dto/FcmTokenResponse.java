package com.attendance.domain.fcm.dto;

import com.attendance.domain.fcm.entity.FcmToken;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** FCM 토큰 응답 DTO - 엔티티를 그대로 반환하지 않고 필요한 필드만 골라 내보내기 위한 변환 계층 */
@Getter
@Builder
@AllArgsConstructor
public class FcmTokenResponse {

  private Long id;
  private String token;
  private String deviceType;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 엔티티를 Response DTO로 변환 */
  public static FcmTokenResponse from(FcmToken fcmToken) {
    return FcmTokenResponse.builder()
        .id(fcmToken.getId())
        .token(fcmToken.getToken())
        .deviceType(fcmToken.getDeviceType())
        .createdAt(fcmToken.getCreatedAt())
        .updatedAt(fcmToken.getUpdatedAt())
        .build();
  }
}
