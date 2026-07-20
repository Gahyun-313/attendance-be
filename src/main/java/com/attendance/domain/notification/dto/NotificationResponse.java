package com.attendance.domain.notification.dto;

import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.entity.NotificationStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 알림 응답 DTO */
@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {

  private Long id;
  private String title;
  private String content;
  private String targetGroup;
  private NotificationStatus status;
  private LocalDateTime scheduledAt;
  private LocalDateTime sentAt;
  private Integer targetCount;
  private Long createdBy;
  private LocalDateTime createdAt;

  /** 엔티티를 Response DTO로 변환 */
  public static NotificationResponse from(Notification notification) {
    return NotificationResponse.builder()
        .id(notification.getId())
        .title(notification.getTitle())
        .content(notification.getContent())
        .targetGroup(notification.getTargetGroup())
        .status(notification.getStatus())
        .scheduledAt(notification.getScheduledAt())
        .sentAt(notification.getSentAt())
        .targetCount(notification.getTargetCount())
        .createdBy(notification.getCreatedBy())
        .createdAt(notification.getCreatedAt())
        .build();
  }
}
