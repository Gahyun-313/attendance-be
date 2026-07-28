package com.attendance.domain.attendance.event;

import lombok.Getter;

/**
 * 출석 체크인 완료 이벤트 - 트랜잭션 커밋 후 실시간 WebSocket 푸시를 트리거하기 위한 애플리케이션 이벤트.
 *
 * <p>이벤트에는 DB PK만 담아 가볍게 만든다. AttendanceService.checkIn()의 트랜잭션이 아직 커밋되지 않은 시점에 이 이벤트가 만들어지므로, 그
 * 시점의 값을 그대로 실어 보내면 이후 롤백되거나 다른 값으로 또 바뀔 여지가 있다. 그래서 리스너(AttendanceEventListener)가 커밋이 확정된 뒤 이 PK로
 * 다시 조회해서 최신 상태를 읽도록 설계했다.
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
