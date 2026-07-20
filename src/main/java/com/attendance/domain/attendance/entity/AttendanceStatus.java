package com.attendance.domain.attendance.entity;

/** 출석 상태 Enum */
public enum AttendanceStatus {
  WAITING, // 대기 (아직 출석 체크 전)
  PRESENT, // 출석
  LATE, // 지각
  ABSENT // 결석
}
