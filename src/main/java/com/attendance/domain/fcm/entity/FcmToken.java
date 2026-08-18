package com.attendance.domain.fcm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 푸시 토큰 엔티티. 사용자의 FCM 토큰을 저장해두고 알림 발송 시 대상 토큰을 찾아 쓴다.
 * 한 사용자가 여러 기기(폰 교체, 재설치 등)를 쓸 수 있어 "User 1 : FcmToken 여러 개" 관계로 설계했다.
 */
@Entity
@Table(
    name = "fcm_tokens",
    indexes = {
      @Index(name = "idx_fcm_user_id", columnList = "user_id"),
      @Index(name = "idx_fcm_token", columnList = "token")
    })
@Getter
@NoArgsConstructor(
    access =
        AccessLevel.PROTECTED) // JPA는 기본 생성자가 필요하지만, 외부에서 new로 만들지 못하게 protected로 막고 @Builder만 열어둔다.
public class FcmToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 토큰 주인의 User ID만 저장(AttendanceRecord와 같은 패턴). 단순 관계는 연관관계 매핑 없이
  // ID만 저장하는 편이 지연 로딩/N+1 부담이 없어 더 가볍다.
  @Column(name = "user_id", nullable = false)
  private Long userId;

  // FCM 토큰 값(앱 재설치나 갱신 시 바뀐다). unique 제약으로 중복 저장을 DB 레벨에서 차단한다.
  @Column(nullable = false, unique = true, length = 255)
  private String token;

  // 어떤 기기에서 온 토큰인지 표시(AOS/iOS/WEB). 지금은 Android 앱뿐이라 기본값은 "AOS"이며, 추후 확장을 대비한 필드다.
  @Column(name = "device_type", length = 20)
  private String deviceType;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  public FcmToken(Long userId, String token, String deviceType) {
    this.userId = userId;
    this.token = token;
    this.deviceType = deviceType != null ? deviceType : "AOS";
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  /** 토큰 소유자 변경. 같은 기기에서 A로 로그인해 등록한 토큰을 로그아웃 후 B로 재로그인했을 때 소유자만 바꾸는 용도로 사용한다. */
  public void reassignTo(Long userId) {
    this.userId = userId;
  }
}
