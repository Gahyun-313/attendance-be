package com.attendance.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.entity.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * NotificationRepository 커스텀 쿼리 테스트 @DataJpaTest로 인메모리 H2 DB에 실제 Notification을 저장하고, 상태
 * 필터(findByStatus)와 학생 노출 범위 쿼리(findVisibleToGroup)가 정확히 동작하는지 검증한다.
 *
 * <p>검증하는 주요 정책: - 상태별로 정확히 필터링된다 (findByStatus) - 전체발송(targetGroup null) 알림은 어떤 그룹명으로 조회해도 노출 대상에
 * 포함된다 (findVisibleToGroup) - 특정 그룹 대상 알림은 해당 그룹명일 때만 노출되고, 다른 그룹명이면 제외된다 - SENT가 아닌 알림은 대상 그룹이 맞아도
 * 학생에게 노출되지 않는다
 */
@DataJpaTest
class NotificationRepositoryTest {

  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TestEntityManager em;

  private Notification save(String title, String targetGroup, NotificationStatus status) {
    // 저장 후 필요하면 상태를 전이시키고 다시 flush (markSent/markFailed/cancel은 도메인 메서드로만 상태를 바꿀 수 있음)
    Notification notification =
        Notification.builder()
            .title(title)
            .content("내용")
            .targetGroup(targetGroup)
            .createdBy(1L)
            .build();
    em.persistAndFlush(notification);

    if (status == NotificationStatus.SENT) {
      notification.markSent(1);
    } else if (status == NotificationStatus.FAILED) {
      notification.markFailed();
    } else if (status == NotificationStatus.CANCELED) {
      notification.cancel();
    }
    em.flush();
    return notification;
  }

  @Nested
  @DisplayName("findByStatus()")
  class FindByStatus {

    @Test
    @DisplayName("지정한 상태의 알림만 조회된다")
    void filtersOnlyMatchingStatus() {
      // given
      save("발송완료", "A반", NotificationStatus.SENT);
      save("예약중", "A반", NotificationStatus.SCHEDULED);

      // when
      Page<Notification> result =
          notificationRepository.findByStatus(NotificationStatus.SENT, PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getTitle()).isEqualTo("발송완료");
    }
  }

  @Nested
  @DisplayName("findVisibleToGroup()")
  class FindVisibleToGroup {

    @Test
    @DisplayName("전체발송(targetGroup null) 알림은 어떤 그룹으로 조회해도 포함된다")
    void broadcastNotification_visibleToAnyGroup() {
      // given
      save("전체공지", null, NotificationStatus.SENT);

      // when
      Page<Notification> result =
          notificationRepository.findVisibleToGroup(
              NotificationStatus.SENT, "A반", PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("특정 그룹 대상 알림은 다른 그룹명으로 조회하면 보이지 않는다")
    void groupSpecificNotification_hiddenFromOtherGroup() {
      // given
      save("A반 공지", "A반", NotificationStatus.SENT);

      // when
      Page<Notification> result =
          notificationRepository.findVisibleToGroup(
              NotificationStatus.SENT, "B반", PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("SENT가 아닌 알림은 대상 그룹이 맞아도 조회되지 않는다")
    void nonSentNotification_excludedEvenIfGroupMatches() {
      // given
      save("예약중", "A반", NotificationStatus.SCHEDULED);

      // when
      Page<Notification> result =
          notificationRepository.findVisibleToGroup(
              NotificationStatus.SENT, "A반", PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).isEmpty();
    }
  }
}
