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

/** 알림 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final FcmTokenRepository fcmTokenRepository;

  /**
   * 알림 생성 (ADMIN) - 저장 직후 발송 대상 시각(scheduledAt)이 지금이거나 없으면 바로 발송 처리(dispatch)까지 수행한다. - 미래 시각으로 예약된
   * 경우 SCHEDULED 상태로만 남고, 실제로 그 시각에 자동 발송해주는 스케줄러는 아직 없다 (추후 Day 6 이후 배치/스케줄러 도입 시점에 "SCHEDULED이면서
   * scheduledAt이 지난 알림"을 찾아 dispatch하는 방식으로 이어붙일 예정)
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
   * 알림 목록 조회 - ADMIN: 전체 알림을 상태 필터(선택)와 함께 조회 (알림 관리 화면용) - STUDENT: 본인 그룹(또는 전체발송) 대상 + 발송완료(SENT)
   * 알림만 조회 ("내 알림함") - 요청받은 status 파라미터는 무시하고 항상 SENT로 강제한다. 다른 그룹 대상이거나 예약/실패/취소 상태인 알림이 노출되면 안 되기
   * 때문
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

  /** 알림 취소 (ADMIN) - 아직 발송되지 않은(SCHEDULED) 알림만 취소 가능 */
  @Transactional
  public void cancelNotification(Long notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND));
    notification.cancel(); // SCHEDULED가 아니면 NOTIFICATION_ALREADY_PROCESSED 예외
  }

  /**
   * 알림 발송 처리 - 대상 그룹(또는 전체) 학생들의 FCM 토큰 등록 여부만 확인해서 SENT/FAILED로 전이시킨다. - 실제 FCM 푸시 발송(Firebase
   * Admin SDK의 FirebaseMessaging.send 등 호출)은 build.gradle의 firebase-admin 의존성이 아직 비활성화 상태라 구현하지 않았다
   * - 의존성 활성화 후 이 메서드 안에 실제 발송 호출을 추가하면 된다.
   */
  private void dispatch(Notification notification) {
    List<User> targets =
        notification.getTargetGroup() != null
            ? userRepository.findByRoleAndGroupName(UserRole.STUDENT, notification.getTargetGroup())
            : userRepository.findByRole(UserRole.STUDENT);

    int tokenCount =
        targets.stream()
            .mapToInt(user -> fcmTokenRepository.findAllByUserId(user.getId()).size())
            .sum();

    if (tokenCount == 0) {
      notification.markFailed();
      return;
    }

    // TODO(Phase 4 후속): firebase-admin 활성화 후 실제 발송 호출로 교체
    notification.markSent(tokenCount);
  }
}
