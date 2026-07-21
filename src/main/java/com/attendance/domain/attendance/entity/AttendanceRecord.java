package com.attendance.domain.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** 출석 기록 엔티티 userId + sessionId Unique 제약 -> 중복 출석 방지 */
@Entity
@Table(
    name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id"}),
        // AttendanceRepository.findByUserId(userId, pageable)가 checkInTime DESC로 정렬 조회하는데,
        // user_id 단일 인덱스만으로는 정렬까지 커버 못 해 filesort가 발생한다. (user_id, check_in_time)
        // 복합 인덱스를 추가해 필터링+정렬을 인덱스 하나로 처리되게 한다 (컬럼 나열 순서가 중요 - user_id가
        // 앞에 있어야 "특정 유저의 기록을 시간순으로"라는 조회 패턴에 맞는 leftmost prefix가 성립한다).
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

  /**
   * 체크인 처리 - 세션 시작 시 사전 생성된 WAITING 레코드를 실제 체크인 정보로 갱신한다. (AttendanceService.checkIn에서 기존 WAITING
   * 레코드가 있을 때 사용, 없으면 새 레코드를 생성)
   */
  public void checkIn(
      AttendanceStatus status, LocalDateTime checkInTime, String nfcTagUid, String nfcLocation) {
    this.status = status;
    this.checkInTime = checkInTime;
    this.nfcTagUid = nfcTagUid;
    this.nfcLocation = nfcLocation;
  }
}
