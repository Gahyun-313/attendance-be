package com.attendance.domain.notification.entity;

import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 엔티티. 관리자가 만든 "발송 단위" 하나를 표현한다. 수신자별 읽음 여부 등 개인화 내역은 없고, targetGroup 조건으로 학생이 자신에게 해당하는 알림만 걸러
 * 본다(findVisibleToGroup 참고). 실제 FCM 발송은 firebase-admin이 비활성화 상태라, 지금은 토큰 존재 여부만 확인해 SENT/FAILED로
 * 전이시키는 것까지만 구현했다.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
      @Index(name = "idx_notification_status", columnList = "status"),
      @Index(name = "idx_notification_target_group", columnList = "target_group")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String content;

  // 대상 그룹. null이면 전체 학생이 대상이며, User.groupName과 매칭한다.
  @Column(name = "target_group", length = 100)
  private String targetGroup;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status;

  // 예약 발송 시각. null이거나 과거 시각이면 생성 즉시 발송을 시도한다(isDue() 참고).
  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  // 실제 발송 처리(SENT/FAILED로 전이)된 시각
  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  // 발송 시점에 확인된 대상 토큰 수 스냅샷. 발송 전(SCHEDULED)이면 null이다.
  @Column(name = "target_count")
  private Integer targetCount;

  // 알림을 생성한 관리자의 userId
  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  public Notification(
      String title, String content, String targetGroup, LocalDateTime scheduledAt, Long createdBy) {
    this.title = title;
    this.content = content;
    this.targetGroup = targetGroup;
    this.scheduledAt = scheduledAt;
    this.createdBy = createdBy;
    this.status = NotificationStatus.SCHEDULED; // 생성 시 기본값이며, dispatch() 결과에 따라 SENT/FAILED로 전이된다.
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

  /** 지금 즉시 발송 대상인지 확인. scheduledAt이 없거나 이미 지난 시각이면 true를 반환한다. */
  public boolean isDue(LocalDateTime now) {
    return scheduledAt == null || !scheduledAt.isAfter(now);
  }

  /** 발송 완료 상태로 전이. SCHEDULED 상태에서만 호출된다(dispatch 내부용이라 상태 검증은 생략한다). */
  public void markSent(int targetCount) {
    this.status = NotificationStatus.SENT;
    this.sentAt = LocalDateTime.now();
    this.targetCount = targetCount;
  }

  /** 발송 실패 상태로 전이. 대상 토큰이 하나도 없을 때 호출한다. */
  public void markFailed() {
    this.status = NotificationStatus.FAILED;
    this.sentAt = LocalDateTime.now();
    this.targetCount = 0;
  }

  /** 발송 취소. SCHEDULED 상태일 때만 가능하며, 이미 처리됐거나(SENT/FAILED) 취소된 알림이면 예외를 던진다. */
  public void cancel() {
    if (this.status != NotificationStatus.SCHEDULED) {
      throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_PROCESSED);
    }
    this.status = NotificationStatus.CANCELED;
  }
}
