package com.attendance.domain.attendance.dto;

import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.user.entity.User;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 세션 구분 없이 조회하는 최근 체크인 기록 응답 DTO - GET /api/attendances/recent (ADMIN) 사용자 이름/세션명까지 함께 내려줘 프론트에서 별도
 * 조인 없이 표로 바로 사용할 수 있게 한다.
 */
@Getter
@Builder
@AllArgsConstructor
public class RecentAttendanceResponse {

  private Long id;
  private Long userId;
  private String userName;
  private String groupName;
  private Long sessionId;
  private String sessionTitle;
  private AttendanceStatus status;
  private LocalDateTime checkInTime;

  /** 엔티티 + User + AttendanceSession을 Response로 변환. user/session이 null이면 해당 필드는 null로 둔다. */
  public static RecentAttendanceResponse of(
      AttendanceRecord record, User user, AttendanceSession session) {
    return RecentAttendanceResponse.builder()
        .id(record.getId())
        .userId(record.getUserId())
        .userName(user != null ? user.getName() : null)
        .groupName(user != null ? user.getGroupName() : null)
        .sessionId(record.getSessionId())
        .sessionTitle(session != null ? session.getTitle() : null)
        .status(record.getStatus())
        .checkInTime(record.getCheckInTime())
        .build();
  }
}
