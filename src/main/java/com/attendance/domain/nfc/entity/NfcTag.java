package com.attendance.domain.nfc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** NFC 태그 엔티티 출석 체크에 사용되는 NFC 태그의 정보를 저장 */
@Entity
@Table(
    name = "nfc_tags",
    indexes = {
      @Index(name = "idx_nfc_uid", columnList = "uid"),
      @Index(name = "idx_nfc_status", columnList = "status")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NfcTag {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String uid;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 255)
  private String description;

  @Column(length = 100)
  private String location;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NfcTagStatus status;

  // 마지막 사용 시각 - 태그 대시보드(오늘 인식 횟수 등) 및 관리 화면에서 사용
  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  public NfcTag(String uid, String name, String description, String location, NfcTagStatus status) {
    this.uid = uid;
    this.name = name;
    this.description = description;
    this.location = location;
    this.status = status != null ? status : NfcTagStatus.ACTIVE;
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

  public void updateInfo(String name, String description, String location) {
    if (name != null) this.name = name;
    if (description != null) this.description = description;
    if (location != null) this.location = location;
  }

  public void activate() {
    this.status = NfcTagStatus.ACTIVE;
  }

  public void deactivate() {
    this.status = NfcTagStatus.INACTIVE;
  }

  public boolean isActive() {
    return this.status == NfcTagStatus.ACTIVE;
  }

  // 태그 사용 시각 기록 - 출석 체크 시 해당 태그가 스캔될 때마다 호출
  public void markUsed() {
    this.lastUsedAt = LocalDateTime.now();
  }
}
