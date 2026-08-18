package com.attendance.domain.notification.service;

import com.attendance.domain.fcm.repository.FcmTokenRepository;
import com.attendance.domain.notification.dto.NotificationRequest;
import com.attendance.domain.notification.dto.NotificationResponse;
import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.entity.NotificationStatus;
import com.attendance.domain.notification.repository.NotificationRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 비즈니스 로직 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final FcmTokenRepository fcmTokenRepository;

  /**
   * 알림 생성(ADMIN) 저장 직후 발송 대상 시각(scheduledAt)이 지금이거나 없으면 바로 dispatch까지 수행한다. 미래 예약이면 SCHEDULED 상태로만
   * 남으며, 자동 발송 스케줄러는 아직 없다(추후 배치 도입 시 이어붙일 예정).
   */
  @Transactional
  public NotificationResponse createNotification(NotificationRequest request, Long adminUserId) {
    Notification notification = notificationRepository.save(request.toEntity(adminUserId));
    if (notification.isDue(LocalDateTime.now())) {
      dispatch(notification);
    }
    return NotificationResponse.from(notification);
  }

  /**
   * 알림 목록 조회 ADMIN은 상태 필터(선택)를 포함해 전체를 조회한다. STUDENT는 본인 그룹(또는 전체발송)이면서 SENT인 알림만 "내 알림함"으로 조회하며,
   * status 파라미터는 무시하고 예약/실패/취소 상태 노출을 막기 위해 항상 SENT로 고정한다.
   */
  public Page<NotificationResponse> getNotifications(
      String role, String groupName, NotificationStatus status, Pageable pageable) {
    if ("ADMIN".equals(role)) {
      Page<Notification> page =
          status != null
              ? notificationRepository.findByStatus(status, pageable)
              : notificationRepository.findAll(pageable);
      return page.map(NotificationResponse::from);
    }
    return notificationRepository
        .findVisibleToGroup(NotificationStatus.SENT, groupName, pageable)
        .map(NotificationResponse::from);
  }

  /** 알림 취소(ADMIN). 아직 발송되지 않은(SCHEDULED) 알림만 취소할 수 있다. */
  @Transactional
  public void cancelNotification(Long notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND));
    notification.cancel(); // SCHEDULED가 아니면 NOTIFICATION_ALREADY_PROCESSED 예외를 던진다.
  }

  /**
   * 알림 발송 처리 실제 FCM 푸시 발송은 firebase-admin 의존성이 비활성화 상태라 아직 구현하지 않았고, 대상 학생들의 FCM 토큰 등록 여부만 확인해
   * SENT/FAILED로 전이시킨다.
   */
  private void dispatch(Notification notification) {
    // 대상 그룹이 지정돼 있으면 해당 그룹만, 없으면 전체 학생을 대상으로 한다.
    List<User> targets =
        notification.getTargetGroup() != null
            ? userRepository.findByRoleAndGroupName(UserRole.STUDENT, notification.getTargetGroup())
            : userRepository.findByRole(UserRole.STUDENT);

    // 대상 전체 FCM 토큰 등록 개수 합산
    int tokenCount =
        targets.stream()
            .mapToInt(user -> fcmTokenRepository.findAllByUserId(user.getId()).size())
            .sum();

    if (tokenCount == 0) {
      notification.markFailed();
      return;
    }

    // TODO(Phase 4 후속): firebase-admin 활성화 후 실제 발송 호출로 교체한다.
    notification.markSent(tokenCount);
  }
}
