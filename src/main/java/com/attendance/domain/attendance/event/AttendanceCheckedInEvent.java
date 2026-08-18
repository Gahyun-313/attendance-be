package com.attendance.domain.attendance.event;

import lombok.Getter;

/**
 * 출석 체크인 완료 이벤트. 커밋 후 실시간 WebSocket 푸시를 트리거하는 애플리케이션 이벤트다. DB PK만 담아 가볍게 만든다. 발행 시점은 아직 트랜잭션 커밋 전이라
 * 값을 그대로 실으면 롤백/변경 여지가 있어서, 리스너(AttendanceEventListener)가 커밋 확정 후 PK로 다시 조회해 최신 상태를 읽도록 설계했다.
 */
@Getter
public class AttendanceCheckedInEvent {

  private final Long sessionId;
  private final Long attendanceRecordId;
  private final Long organizerId;

  public AttendanceCheckedInEvent(Long sessionId, Long attendanceRecordId, Long organizerId) {
    this.sessionId = sessionId;
    this.attendanceRecordId = attendanceRecordId;
    this.organizerId = organizerId;
  }
}
