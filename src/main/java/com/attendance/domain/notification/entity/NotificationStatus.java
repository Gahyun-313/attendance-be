package com.attendance.domain.notification.entity;

/** 알림 발송 상태 Enum */
public enum NotificationStatus {
    SCHEDULED, // 예약됨 (아직 발송 전, scheduledAt이 미래이거나 발송 처리 대기 중)
    SENT, // 발송 완료
    FAILED, // 발송 실패 (대상 그룹에 FCM 토큰을 등록한 사용자가 하나도 없는 경우 등)
    CANCELED // 관리자가 발송 전 취소
}