package com.attendance.domain.session;

/** 출석 세션 상태 Enum */
public enum SessionStatus {
  SCHEDULED, // 예정됨
  ACTIVE, // 진행 중
  COMPLETED, // 완료됨
  CANCELED // 취소됨
}
