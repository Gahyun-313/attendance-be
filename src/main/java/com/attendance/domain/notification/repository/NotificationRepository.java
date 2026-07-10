package com.attendance.domain.notification.repository;

import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림 Repository */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 상태별 필터링 (관리자 알림 관리 화면 - 전체/예약/발송완료/실패) */
    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    /**
     * 학생에게 노출 가능한 알림만 조회 - 전체발송(targetGroup null) 또는 본인 그룹 대상 알림 중,
     * 지정한 상태(SENT 고정으로 사용)인 것만. 다른 그룹 대상이거나 예약/실패/취소 상태인 알림은 학생에게 노출하지 않는다.
     *
     * Page<T> 반환 + 괄호(OR)가 섞인 WHERE절 조합은 Spring Data가 count 쿼리를 자동 유추할 때
     * 파라미터를 놓쳐 QueryParameterException이 나는 경우가 있어, countQuery를 명시적으로 같이 지정한다.
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