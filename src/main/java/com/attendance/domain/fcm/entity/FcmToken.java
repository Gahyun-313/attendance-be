package com.attendance.domain.fcm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 푸시 토큰 엔티티 - 사용자가 앱을 설치/로그인하면 발급받는 FCM 토큰을 저장해두고, 나중에 알림을 보낼 때 이 테이블에서 대상 토큰을 찾아 쓴다. - 한 사용자가
 * 여러 기기(폰 교체, 재설치 등)를 쓸 수 있으므로 "User 1 : FcmToken 여러 개" 관계로 설계했다.
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
        AccessLevel.PROTECTED) // JPA는 기본 생성자가 필요하지만, 외부에서 new로 못 만들게 protected + @Builder만 열어둔다
public class FcmToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 토큰 주인의 User ID만 저장한다 (User를 @ManyToOne으로 직접 물고 있지 않음).
  // 이 프로젝트의 AttendanceRecord와 같은 방식 - "몇 번 유저 것인지"만 알면 되는 단순 관계는
  // 연관관계 매핑 대신 ID만 저장하는 편이 지연 로딩/N+1 같은 걸 신경 쓸 필요가 없어 더 가볍다.
  @Column(name = "user_id", nullable = false)
  private Long userId;

  // FCM 토큰 값. 앱 재설치나 토큰 갱신 시 값이 바뀐다.
  // unique 제약을 걸어 같은 토큰이 두 번 저장되는 걸 DB 레벨에서 원천 차단한다.
  @Column(nullable = false, unique = true, length = 255)
  private String token;

  // 어떤 기기에서 온 토큰인지 (AOS/iOS/WEB). 지금은 Android 앱뿐이라 기본값 "AOS", 추후 확장 대비용 필드.
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

  /**
   * 토큰 소유자를 바꾼다. 예: 같은 기기에서 A 계정으로 로그인해 토큰을 등록했다가 로그아웃 후 B 계정으로 다시 로그인한 경우, 토큰 값(기기 단위)은 그대로인데 주인만
   * A → B로 바뀌어야 한다. 이럴 때 사용.
   */
  public void reassignTo(Long userId) {
    this.userId = userId;
  }
}
