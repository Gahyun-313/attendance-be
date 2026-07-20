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
 * 알림 엔티티 - 관리자가 만든 "발송 단위" 하나를 표현한다 (수신자별 읽음 여부 등 개인화된 내역은 별도 테이블 없이, targetGroup 조건으로 학생이 자신에게
 * 해당하는 알림만 걸러보는 방식으로 근사한다 - NotificationRepository.findVisibleToGroup 참고) - 실제 FCM 푸시 발송은
 * firebase-admin 의존성이 아직 비활성화 상태라 이번 Phase에서는 대상자의 토큰 존재 여부만 확인하고 SENT/FAILED로 전이시키는 것까지만 구현한다
 * (NotificationService.dispatch 참고)
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

  // 대상 그룹 - null이면 전체 학생 대상 (User.groupName과 매칭)
  @Column(name = "target_group", length = 100)
  private String targetGroup;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status;

  // 예약 발송 시각 - null이거나 과거 시각이면 생성 즉시 발송 시도 (isDue() 참고)
  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  // 실제 발송 처리(SENT/FAILED로 전이)된 시각
  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  // 발송 시점에 확인된 대상 토큰 수 스냅샷 - 발송 전(SCHEDULED)이면 null
  @Column(name = "target_count")
  private Integer targetCount;

  // 알림을 생성한 관리자 userId
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
    this.status = NotificationStatus.SCHEDULED; // 생성 시 기본값 - dispatch() 결과에 따라 SENT/FAILED로 전이됨
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

  /** 지금 즉시 발송 대상인지 - scheduledAt이 없거나 이미 지난 시각이면 true */
  public boolean isDue(LocalDateTime now) {
    return scheduledAt == null || !scheduledAt.isAfter(now);
  }

  /** 발송 완료 처리 - SCHEDULED 상태에서만 호출됨(dispatch 내부용이라 상태 검증은 생략) */
  public void markSent(int targetCount) {
    this.status = NotificationStatus.SENT;
    this.sentAt = LocalDateTime.now();
    this.targetCount = targetCount;
  }

  /** 발송 실패 처리 - 대상 토큰이 하나도 없을 때 */
  public void markFailed() {
    this.status = NotificationStatus.FAILED;
    this.sentAt = LocalDateTime.now();
    this.targetCount = 0;
  }

  /** 발송 취소 - SCHEDULED 상태일 때만 가능, 이미 처리된(SENT/FAILED) 알림이나 이미 취소된 알림은 예외 */
  public void cancel() {
    if (this.status != NotificationStatus.SCHEDULED) {
      throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_PROCESSED);
    }
    this.status = NotificationStatus.CANCELED;
  }
}
