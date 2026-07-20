package com.attendance.domain.attendance.dto;

import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.user.entity.User;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 출석 기록 응답 DTO - 이름/학번/그룹은 User에서 조회해 채움 (UserService는 Day 3라 Repository만 사용) */
@Getter
@Builder
@AllArgsConstructor
public class AttendanceResponse {

  private Long id;
  private Long userId;
  private String userName; // User.name
  private String studentId; // User.studentId
  private String groupName; // User.groupName
  private Long sessionId;
  private AttendanceStatus status;
  private LocalDateTime checkInTime;
  private String nfcTagUid;
  private String nfcLocation;
  private String modifiedBy;
  private String modifyReason;
  private String note;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 엔티티 + User를 Response로 변환. user가 null이면 사용자 정보 필드는 null로 둔다 (레코드는 있으나 사용자 조회 실패 등 방어). */
  public static AttendanceResponse from(AttendanceRecord attendanceRecord, User user) {
    return AttendanceResponse.builder()
        .id(attendanceRecord.getId())
        .userId(attendanceRecord.getUserId())
        .userName(user != null ? user.getName() : null)
        .studentId(user != null ? user.getStudentId() : null)
        .groupName(user != null ? user.getGroupName() : null)
        .sessionId(attendanceRecord.getSessionId())
        .status(attendanceRecord.getStatus())
        .checkInTime(attendanceRecord.getCheckInTime())
        .nfcTagUid(attendanceRecord.getNfcTagUid())
        .nfcLocation(attendanceRecord.getNfcLocation())
        .modifiedBy(attendanceRecord.getModifiedBy())
        .modifyReason(attendanceRecord.getModifyReason())
        .note(attendanceRecord.getNote())
        .createdAt(attendanceRecord.getCreatedAt())
        .updatedAt(attendanceRecord.getUpdatedAt())
        .build();
  }
}
