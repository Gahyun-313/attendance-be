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
 * 출석 체크인 이벤트를 받아 WebSocket으로 실시간 푸시하는 리스너.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}: checkIn()의 트랜잭션이 "성공적으로 커밋된 뒤"에만
 * 실행된다. 만약 일반 {@code @EventListener}를 썼다면 checkIn() 트랜잭션이 아직 커밋되지 않았거나 이후 롤백될 수도 있는
 * 시점에 실행되어, 클라이언트에 "체크인 성공" 푸시를 보냈는데 실제로는 DB에 반영이 안 된 상태가 될 위험이 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceEventListener {

    private final AttendanceService attendanceService;
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckedIn(AttendanceCheckedInEvent event) {
        try {
            // 커밋 후 시점에 PK로 다시 조회 - 이벤트 발행 시점 값이 아니라 확정된 최신 상태를 읽는다
            AttendanceResponse record = attendanceService.getAttendanceRecord(event.getAttendanceRecordId());
            AttendanceDashboardResponse dashboard =
                    attendanceService.getSessionDashboard(event.getSessionId());

            AttendanceCheckInPush payload = AttendanceCheckInPush.of(record, dashboard);
            messagingTemplate.convertAndSend("/topic/attendance/" + event.getSessionId(), payload);
        } catch (Exception e) {
            // 실시간 푸시가 실패해도 이미 체크인 자체는 커밋되어 성공한 상태다.
            // 여기서 예외를 던지면 안 되는(던져봤자 되돌릴 트랜잭션도 없는) 이유가 그것 - 로그만 남기고 삼킨다.
            log.error(
                    "출석 체크인 실시간 푸시 실패 - sessionId: {}, attendanceRecordId: {}",
                    event.getSessionId(),
                    event.getAttendanceRecordId(),
                    e);
        }
    }
}