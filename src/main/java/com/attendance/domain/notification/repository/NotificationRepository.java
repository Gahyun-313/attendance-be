package com.attendance.domain.notification.repository;

import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림에 대한 조회 처리 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /** 상태별로 알림 필터링(관리자 알림 관리 화면의 전체/예약/발송완료/실패 필터) */
  Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

  /**
   * 학생에게 노출 가능한 알림만 조회. 전체발송(targetGroup null) 또는 본인 그룹 대상이면서 지정 상태(SENT 고정)인 알림만 필터링. countQuery를
   * 명시한 이유는, Page 반환에 OR가 섞인 WHERE절 조합에서 Spring Data가 count 쿼리를 자동으로 유추할 때 파라미터를 놓쳐
   * QueryParameterException이 나는 경우가 있기 때문이다.
   */
  @Query(
      value =
          "SELECT n FROM Notification n WHERE n.status = :status "
              + "AND (n.targetGroup IS NULL OR n.targetGroup = :groupName)",
      countQuery =
          "SELECT COUNT(n) FROM Notification n WHERE n.status = :status "
              + "AND (n.targetGroup IS NULL OR n.targetGroup = :groupName)")
  Page<Notification> findVisibleToGroup(
      @Param("status") NotificationStatus status,
      @Param("groupName") String groupName,
      Pageable pageable);
}
