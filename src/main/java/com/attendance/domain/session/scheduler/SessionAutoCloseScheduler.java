package com.attendance.domain.session.scheduler;

import com.attendance.domain.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종료 시각이 지난 세션을 자동으로 정리하는 배치.
 * ACTIVE는 종료 처리 후 자동 결석 처리, 시작 안 된 SCHEDULED는 자동 취소한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAutoCloseScheduler {

  private final SessionService sessionService;

  // 단일 서버 인스턴스 전제라 분산 락 없이 동작한다.
  @Scheduled(cron = "0 * * * * *")
  public void autoTransitionExpiredSessions() {
    for (Long sessionId : sessionService.findExpiredActiveSessionIds()) {
      try {
        sessionService.autoCloseSession(sessionId);
        log.info("세션 자동 종료 처리 완료 - sessionId: {}", sessionId);
      } catch (Exception e) {
        log.error("세션 자동 종료 처리 실패 - sessionId: {}", sessionId, e);
      }
    }

    for (Long sessionId : sessionService.findExpiredScheduledSessionIds()) {
      try {
        sessionService.autoCancelSession(sessionId);
        log.info("세션 자동 취소 처리 완료 - sessionId: {}", sessionId);
      } catch (Exception e) {
        log.error("세션 자동 취소 처리 실패 - sessionId: {}", sessionId, e);
      }
    }
  }
}
