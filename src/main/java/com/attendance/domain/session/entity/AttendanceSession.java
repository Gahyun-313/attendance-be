package com.attendance.domain.session.entity;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.dto.SessionRequest;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/** 출석 세션 엔티티. 하나의 수업 출석 체크 단위를 표현한다. */
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

  // 소속 단체(Organization FK). User.organizationId와 동일한 패턴으로, 연관관계 대신 Long으로 저장한다.
  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 500)
  private String description;

  // 대상 그룹(예: "A반", "1학년"). 어드민 웹에서 그룹별 세션 필터링에 사용.
  @Column(name = "group_name", length = 100)
  private String groupName;

  // 세션 날짜. startTime과 별도로 관리해 날짜별 필터링 조회 편의성을 확보한다.
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

  // 세션에 연결된 NFC 태그. 출석 체크 시 이 태그로만 체크인할 수 있다.
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

  /** 세션 정보 수정 */
  public void update(SessionRequest request, NfcTag nfcTag) {
    this.title = request.getTitle();
    this.description = request.getDescription();
    this.groupName = request.getGroupName();
    this.sessionDate =
        request.getSessionDate() != null
            ? request.getSessionDate()
            : request.getStartTime().toLocalDate();
    this.startTime = request.getStartTime();
    this.endTime = request.getEndTime();
    if (request.getLateThresholdMinutes() != null) {
      this.lateThresholdMinutes = request.getLateThresholdMinutes();
    }
    this.location = request.getLocation();
    this.nfcTag = nfcTag;
    this.note = request.getNote();
  }

  /** 세션 시작(SCHEDULED -> ACTIVE) */
  public void start() {
    if (this.status != SessionStatus.SCHEDULED) {
      throw new BusinessException(ErrorCode.SESSION_NOT_ACTIVE);
    }
    this.status = SessionStatus.ACTIVE;
  }

  /** 세션 종료(ACTIVE -> COMPLETED) */
  public void close() {
    if (this.status != SessionStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.SESSION_NOT_ACTIVE);
    }
    this.status = SessionStatus.COMPLETED;
  }

  /** 세션 취소 */
  public void cancel() {
    if (this.status == SessionStatus.COMPLETED || this.status == SessionStatus.CANCELED) {
      throw new BusinessException(ErrorCode.SESSION_ALREADY_CLOSED);
    }
    this.status = SessionStatus.CANCELED;
  }
}
