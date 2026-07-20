package com.attendance.domain.attendance.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * WebSocket으로 어드민 대시보드에 실시간 전송되는 출석 체크인 이벤트 페이로드.
 *
 * <p>방금 체크인한 레코드 상세(record)와, 그걸 반영한 최신 대시보드 집계(dashboard)를 함께 실어 보낸다. 클라이언트가 "누가 방금 체크인했는지"와 "전체
 * 현황 숫자"를 매번 REST로 다시 조회하지 않고 이 메시지 하나로 갱신할 수 있게 하기 위함.
 */
@Getter
@Builder
public class AttendanceCheckInPush {

  private Long sessionId;
  private AttendanceResponse record;
  private AttendanceDashboardResponse dashboard;

  public static AttendanceCheckInPush of(
      AttendanceResponse record, AttendanceDashboardResponse dashboard) {
    return AttendanceCheckInPush.builder()
        .sessionId(dashboard.getSessionId())
        .record(record)
        .dashboard(dashboard)
        .build();
  }
}
