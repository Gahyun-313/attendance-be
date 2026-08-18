package com.attendance.domain.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** 출석 기록 엔티티. userId + sessionId Unique 제약으로 중복 출석을 방지한다. */
@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id"}),
    // findByUserId가 checkInTime DESC로 정렬 조회하는데, user_id 단일 인덱스만으로는 filesort가 발생한다.
    // (user_id, check_in_time) 복합 인덱스로 필터+정렬을 한 번에 처리한다. user_id가 앞이어야
    // "특정 유저를 시간순으로" 조회하는 패턴의 leftmost prefix가 성립한다.
    indexes = @Index(name = "idx_attendance_user_checkin", columnList = "user_id, check_in_time"))
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

  // 출석 체크 시각. WAITING(대기) 상태일 때는 아직 체크인 전이라 null일 수 있다.
  @Column(name = "check_in_time")
  private LocalDateTime checkInTime;

  @Column(name = "nfc_tag_uid", length = 100)
  private String nfcTagUid;

  // NFC 스캔 위치. 스캔 당시 태그의 설치 위치를 기록해, 태그 위치가 추후 바뀌어도 기록 시점 위치를 보존한다.
  @Column(name = "nfc_location", length = 100)
  private String nfcLocation;

  // 출석 상태 수정자. 관리자가 출석 상태를 수동으로 변경했을 때 그 관리자 이름을 기록한다.
  @Column(name = "modified_by", length = 100)
  private String modifiedBy;

  // 출석 상태 변경 사유. 관리자가 상태를 수정할 때 필수로 작성한다.
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

  /** 관리자가 출석 상태 수정. 변경 사유를 필수로 받아 누가/왜 바꿨는지 함께 기록한다. */
  public void modifyStatus(AttendanceStatus newStatus, String modifiedBy, String reason) {
    this.status = newStatus;
    this.modifiedBy = modifiedBy;
    this.modifyReason = reason;
  }

  /** 체크인 처리. 세션 시작 시 사전 생성된 WAITING 레코드를 실제 체크인 정보로 갱신한다(없으면 새로 생성, AttendanceService.checkIn 참고). */
  public void checkIn(
      AttendanceStatus status, LocalDateTime checkInTime, String nfcTagUid, String nfcLocation) {
    this.status = status;
    this.checkInTime = checkInTime;
    this.nfcTagUid = nfcTagUid;
    this.nfcLocation = nfcLocation;
  }
}
