package com.attendance.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.attendance.domain.fcm.entity.FcmToken;
import com.attendance.domain.fcm.repository.FcmTokenRepository;
import com.attendance.domain.fcm.service.FcmSender;
import com.attendance.domain.fcm.service.FcmSender.FcmSendResult;
import com.attendance.domain.notification.dto.NotificationRequest;
import com.attendance.domain.notification.dto.NotificationResponse;
import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.entity.NotificationStatus;
import com.attendance.domain.notification.repository.NotificationRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * NotificationService 단위 테스트
 *
 * <p>Mockito로 Repository들을 대체하여 알림 생성 시 즉시발송/예약 판정, 발송 성공/실패 처리, 취소 가능 여부, ADMIN/STUDENT 목록 조회 분기
 * 로직을 검증한다.
 *
 * <p>검증하는 주요 정책: - scheduledAt이 없거나 과거면 생성 즉시 발송을 시도한다 (isDue) - 발송 대상자 중 FCM 토큰 보유자가 1명 이상이면 SENT,
 * 없으면 FAILED로 처리한다 - targetGroup이 없으면 전체 학생(findByRole), 있으면 해당 그룹(findByRoleAndGroupName)에서 대상을
 * 찾는다 - scheduledAt이 미래면 발송을 시도하지 않고 SCHEDULED로 남긴다 - SCHEDULED 상태만 취소 가능하고, 이미 처리된 알림을 취소하면 예외가
 * 발생한다 - 존재하지 않는 알림을 취소하면 NOTIFICATION_NOT_FOUND 예외가 발생한다 - ADMIN은 전체(또는 상태 필터) 목록을, STUDENT는 본인 그룹
 * 대상 SENT 알림만 조회한다 (요청 status는 무시)
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private UserRepository userRepository;
  @Mock private FcmTokenRepository fcmTokenRepository;
  @Mock private FcmSender fcmSender;
  @InjectMocks private NotificationService notificationService;

  private void stubSaveReturnsSameEntity() {
    // save()가 호출된 엔티티를 그대로 반환하게 해서, 이후 상태 변화를 같은 인스턴스로 검증할 수 있게 한다
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @Nested
  @DisplayName("createNotification()")
  class CreateNotification {

    @Test
    @DisplayName("즉시발송 대상이고 대상 그룹에 FCM 토큰 보유자가 있으면 SENT로 처리된다")
    void immediateSend_withTokens_marksSent() {
      // given
      // targetGroup="A반" + scheduledAt 없음(즉시발송), 학생 2명 중 1명만 토큰 2개 보유
      stubSaveReturnsSameEntity();
      NotificationRequest request = new NotificationRequest("제목", "내용", "A반", null);
      User student1 = User.builder().id(1L).role(UserRole.STUDENT).groupName("A반").build();
      User student2 = User.builder().id(2L).role(UserRole.STUDENT).groupName("A반").build();
      given(userRepository.findByRoleAndGroupName(UserRole.STUDENT, "A반"))
          .willReturn(List.of(student1, student2));
      given(fcmTokenRepository.findAllByUserId(1L))
          .willReturn(
              List.of(
                  FcmToken.builder().userId(1L).token("t1").build(),
                  FcmToken.builder().userId(1L).token("t2").build()));
      given(fcmTokenRepository.findAllByUserId(2L)).willReturn(List.of());
      given(fcmSender.send(any(), any(), any())).willReturn(new FcmSendResult(2, List.of()));

      // when
      NotificationResponse response = notificationService.createNotification(request, 99L);

      // then
      // 토큰 2개가 확인됐으므로 SENT, targetCount는 그 합계여야 함
      assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(response.getTargetCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("즉시발송 대상인데 대상자 중 FCM 토큰 보유자가 없으면 FAILED로 처리된다")
    void immediateSend_noTokens_marksFailed() {
      // given
      // 대상 학생은 있지만 아무도 FCM 토큰을 등록하지 않은 상황
      stubSaveReturnsSameEntity();
      NotificationRequest request = new NotificationRequest("제목", "내용", "A반", null);
      User student = User.builder().id(1L).role(UserRole.STUDENT).groupName("A반").build();
      given(userRepository.findByRoleAndGroupName(UserRole.STUDENT, "A반"))
          .willReturn(List.of(student));
      given(fcmTokenRepository.findAllByUserId(1L)).willReturn(List.of());

      // when
      NotificationResponse response = notificationService.createNotification(request, 99L);

      // then
      assertThat(response.getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(response.getTargetCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("targetGroup이 없으면 전체 학생을 대상으로 조회한다")
    void noTargetGroup_queriesAllStudents() {
      // given
      // 전체발송(targetGroup null)이므로 그룹 조회가 아닌 전체 학생 조회를 써야 함
      stubSaveReturnsSameEntity();
      NotificationRequest request = new NotificationRequest("제목", "내용", null, null);
      given(userRepository.findByRole(UserRole.STUDENT)).willReturn(List.of());

      // when
      notificationService.createNotification(request, 99L);

      // then
      verify(userRepository).findByRole(UserRole.STUDENT);
      verify(userRepository, never()).findByRoleAndGroupName(any(), any());
    }

    @Test
    @DisplayName("scheduledAt이 미래 시각이면 발송을 시도하지 않고 SCHEDULED로 남는다")
    void futureScheduledAt_staysScheduled() {
      // given
      // 예약 발송 - 아직 실제로 그 시각에 자동 발송해주는 스케줄러는 없어서, 생성 시점엔 SCHEDULED로만 남아야 함
      stubSaveReturnsSameEntity();
      NotificationRequest request =
          new NotificationRequest("제목", "내용", "A반", LocalDateTime.now().plusDays(1));

      // when
      NotificationResponse response = notificationService.createNotification(request, 99L);

      // then
      // 발송 로직(dispatch) 자체가 호출되면 안 되므로 대상자/토큰 조회가 전혀 없어야 함
      assertThat(response.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
      assertThat(response.getTargetCount()).isNull();
      verify(userRepository, never()).findByRoleAndGroupName(any(), any());
      verify(userRepository, never()).findByRole(any());
      verify(fcmTokenRepository, never()).findAllByUserId(any());
    }
  }

  @Nested
  @DisplayName("cancelNotification()")
  class CancelNotification {

    @Test
    @DisplayName("SCHEDULED 상태면 CANCELED로 취소된다")
    void scheduled_cancelsSuccessfully() {
      // given
      Notification notification =
          Notification.builder().title("제목").content("내용").createdBy(1L).build();
      given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

      // when
      notificationService.cancelNotification(10L);

      // then
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELED);
    }

    @Test
    @DisplayName("이미 발송 완료된 알림을 취소하면 NOTIFICATION_ALREADY_PROCESSED 예외가 발생한다")
    void alreadySent_throwsException() {
      // given
      // 이미 SENT로 전이된 알림을 취소하려는 상황
      Notification notification =
          Notification.builder().title("제목").content("내용").createdBy(1L).build();
      notification.markSent(3);
      given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

      // when & then
      assertThatThrownBy(() -> notificationService.cancelNotification(10L))
          .isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.NOTIFICATION_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("존재하지 않는 알림을 취소하면 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void notFound_throwsException() {
      // given
      given(notificationRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> notificationService.cancelNotification(999L))
          .isInstanceOf(EntityNotFoundException.class)
          .extracting(e -> ((EntityNotFoundException) e).getErrorCode())
          .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("getNotifications()")
  class GetNotifications {

    @Test
    @DisplayName("ADMIN이 status 없이 조회하면 전체 목록을 페이징 조회한다")
    void admin_noStatus_findAll() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      Page<Notification> page = new PageImpl<>(List.of());
      given(notificationRepository.findAll(pageable)).willReturn(page);

      // when
      notificationService.getNotifications("ADMIN", null, null, pageable);

      // then
      verify(notificationRepository).findAll(pageable);
      verify(notificationRepository, never()).findByStatus(any(), any());
      verify(notificationRepository, never()).findVisibleToGroup(any(), any(), any());
    }

    @Test
    @DisplayName("ADMIN이 status를 지정하면 해당 상태로 필터링해서 조회한다")
    void admin_withStatus_findByStatus() {
      // given
      Pageable pageable = PageRequest.of(0, 20);
      given(notificationRepository.findByStatus(NotificationStatus.SENT, pageable))
          .willReturn(new PageImpl<>(List.of()));

      // when
      notificationService.getNotifications("ADMIN", null, NotificationStatus.SENT, pageable);

      // then
      verify(notificationRepository).findByStatus(NotificationStatus.SENT, pageable);
    }

    @Test
    @DisplayName("STUDENT는 요청 status와 무관하게 본인 그룹 대상 SENT 알림만 조회한다")
    void student_ignoresRequestedStatus_alwaysQueriesSent() {
      // given
      // STUDENT가 SCHEDULED를 요청해도 무시되고 SENT로 강제 조회되어야 함 (예약/실패/취소 알림 노출 방지)
      Pageable pageable = PageRequest.of(0, 20);
      given(notificationRepository.findVisibleToGroup(NotificationStatus.SENT, "A반", pageable))
          .willReturn(new PageImpl<>(List.of()));

      // when
      notificationService.getNotifications("STUDENT", "A반", NotificationStatus.SCHEDULED, pageable);

      // then
      verify(notificationRepository).findVisibleToGroup(NotificationStatus.SENT, "A반", pageable);
      verify(notificationRepository, never()).findByStatus(any(), any());
      verify(notificationRepository, never()).findAll(any(Pageable.class));
    }
  }
}
