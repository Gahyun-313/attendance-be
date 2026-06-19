package com.attendance.domain.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** 출석 기록 엔티티 userId + sessionId Unique 제약 -> 중복 출석 방지 */
@Entity
@Table(
        name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AttendanceStatus status;

  // 출석 체크 시각 - WAITING(대기) 상태일 때는 아직 체크인 전이므로 null 가능
  @Column(name = "check_in_time")
  private LocalDateTime checkInTime;

  @Column(name = "nfc_tag_uid", length = 100)
  private String nfcTagUid;

  // NFC 스캔 위치 - 스캔 당시 태그의 설치 위치를 기록 (태그 위치가 추후 바뀌어도 기록 시점 위치 보존)
  @Column(name = "nfc_location", length = 100)
  private String nfcLocation;

  // 출석 상태 수정자 - 관리자가 출석 상태를 수동으로 변경했을 때 그 관리자 이름 기록
  @Column(name = "modified_by", length = 100)
  private String modifiedBy;

  // 출석 상태 변경 사유 - 관리자가 상태를 수정할 때 필수로 작성
  @Column(name = "modify_reason", length = 500)
  private String modifyReason;

  @Column(length = 500)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  /** 관리자에 의한 출석 상태 수정 - 변경 사유를 필수로 받아 누가/왜 바꿨는지 함께 기록 */
  public void modifyStatus(AttendanceStatus newStatus, String modifiedBy, String reason) {
    this.status = newStatus;
    this.modifiedBy = modifiedBy;
    this.modifyReason = reason;
  }
}