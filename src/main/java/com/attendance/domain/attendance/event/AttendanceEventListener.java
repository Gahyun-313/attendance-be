package com.attendance.domain.attendance.event;

import com.attendance.domain.attendance.dto.AttendanceCheckInPush;
import com.attendance.domain.attendance.dto.AttendanceDashboardResponse;
import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 출석 체크인 이벤트를 받아 WebSocket으로 실시간 푸시하는 리스너
 * AFTER_COMMIT에서만 실행하는 이유는, 일반 {@code @EventListener}를 쓰면 checkIn() 트랜잭션이
 * 아직 커밋 전이거나 롤백될 수도 있는 시점에 실행돼, 실제 DB엔 반영되지 않은 "체크인 성공" 푸시를
 * 보낼 위험이 있기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceEventListener {

  private final AttendanceService attendanceService;
  private final SimpMessagingTemplate messagingTemplate;

  /** 체크인 커밋 완료 후 최신 상태를 조회해 세션 구독자에게 실시간 푸시 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleCheckedIn(AttendanceCheckedInEvent event) {
    try {
      // 커밋 후 시점에 PK로 다시 조회한다. 이벤트 발행 시점 값이 아니라 확정된 최신 상태를 읽기 위함이다.
      AttendanceResponse record =
          attendanceService.getAttendanceRecord(event.getAttendanceRecordId());
      AttendanceDashboardResponse dashboard =
          attendanceService.getSessionDashboard(event.getSessionId(), event.getOrganizerId());

      // 체크인 결과와 대시보드 스냅샷을 묶어 세션 토픽 구독자에게 전송
      AttendanceCheckInPush payload = AttendanceCheckInPush.of(record, dashboard);
      messagingTemplate.convertAndSend("/topic/attendance/" + event.getSessionId(), payload);
    } catch (Exception e) {
      // 실시간 푸시가 실패해도 체크인 자체는 이미 커밋되어 성공한 상태다.
      // 되돌릴 트랜잭션이 없어 여기서 예외를 던지면 안 되므로, 로그만 남기고 삼킨다.
      log.error(
          "출석 체크인 실시간 푸시 실패 - sessionId: {}, attendanceRecordId: {}",
          event.getSessionId(),
          event.getAttendanceRecordId(),
          e);
    }
  }
}
