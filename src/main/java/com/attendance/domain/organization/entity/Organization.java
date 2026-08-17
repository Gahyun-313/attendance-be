package com.attendance.domain.organization.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 단체(학교/학원) 엔티티. 멀티테넌시의 기준 단위 - User, AttendanceSession 등이 이 엔티티를 FK로 참조해 서로 다른 단체의 데이터를 격리한다. */
@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  // 추가 어드민이 소셜 로그인으로 셀프 조인할 때 쓰는 초대 코드다.
  // 특정 어드민 계정의 아이디와는 별개의 값으로 관리한다.
  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false)
  private Boolean active;

  // 결석 자동 처리 - 세션 종료 시 남은 WAITING 레코드를 자동으로 ABSENT 처리할지 여부
  @Column(name = "auto_absent_enabled", nullable = false)
  private Boolean autoAbsentEnabled = true;

  // NFC 태그 위치 검증 - 체크인 시 세션.location과 태그.location이 다르면 체크인을 막을지 여부
  @Column(name = "nfc_location_validation_enabled", nullable = false)
  private Boolean nfcLocationValidationEnabled = false;

  // 기본 출석 인정 시간 - 세션 생성 폼 기본값으로만 쓰임
  @Column(name = "default_attendance_grace_minutes", nullable = false)
  private Integer defaultAttendanceGraceMinutes = 5;

  // 기본 지각 인정 시간 - 세션 생성 폼 기본값으로만 쓰임
  @Column(name = "default_late_threshold_minutes", nullable = false)
  private Integer defaultLateThresholdMinutes = 10;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  public Organization(String name, String code, Boolean active) {
    this.name = name;
    this.code = code;
    this.active = active != null ? active : true;
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

  public void updateInfo(String name) {
    if (name != null) this.name = name;
  }

  /** 출석 정책 수정 - null인 필드는 변경하지 않음(부분 수정 지원) * */
  public void updatePolicy(
      Boolean autoAbsentEnabled,
      Boolean nfcLocationValidationEnabled,
      Integer defaultAttendanceGraceMinutes,
      Integer defaultLateThresholdMinutes) {
    if (autoAbsentEnabled != null) this.autoAbsentEnabled = autoAbsentEnabled;
    if (nfcLocationValidationEnabled != null) {
      this.nfcLocationValidationEnabled = nfcLocationValidationEnabled;
    }
    if (defaultAttendanceGraceMinutes != null) {
      this.defaultAttendanceGraceMinutes = defaultAttendanceGraceMinutes;
    }
    if (defaultLateThresholdMinutes != null) {
      this.defaultLateThresholdMinutes = defaultLateThresholdMinutes;
    }
  }

  // 초대 코드 재발급 - 코드가 유출됐을 때 기존 코드를 무효화하는 용도.
  public void reissueCode(String newCode) {
    this.code = newCode;
  }

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }

  public boolean isActive() {
    return this.active;
  }
}
