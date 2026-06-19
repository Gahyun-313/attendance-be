package com.attendance.domain.session.entity;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.session.SessionStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/** 출석 세션 엔티티 하나의 수업 출석 체크 단위 */
@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 500)
  private String description;

  // 대상 그룹 (예: "A반", "1학년") - 어드민 웹에서 그룹별 세션 필터링에 사용
  @Column(name = "group_name", length = 100)
  private String groupName;

  // 세션 날짜 - 날짜별 필터링용 (startTime과 별도로 관리해 조회 편의성 확보)
  @Column(name = "session_date")
  private LocalDate sessionDate;

  @Column(name = "start_time", nullable = false)
  private LocalDateTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalDateTime endTime;

  @Builder.Default
  @Column(name = "late_threshold_minutes", nullable = false)
  private Integer lateThresholdMinutes = 10;

  @Column(length = 100)
  private String location;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SessionStatus status;

  // 세션에 연결된 NFC 태그 - 출석 체크 시 해당 태그로만 체크인 가능
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "nfc_tag_id")
  private NfcTag nfcTag;

  // 비고
  @Column(length = 500)
  private String note;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

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

  /** 세션이 현재 활성 상태인지 확인 */
  public boolean isActive() {
    LocalDateTime now = LocalDateTime.now();
    return status == SessionStatus.ACTIVE && now.isAfter(startTime) && now.isBefore(endTime);
  }

  /** 주어진 시간이 지각인지 확인 */
  public boolean isLate(LocalDateTime checkInTime) {
    return checkInTime.isAfter(startTime.plusMinutes(lateThresholdMinutes));
  }
}