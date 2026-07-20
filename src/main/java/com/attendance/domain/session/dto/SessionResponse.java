package com.attendance.domain.session.dto;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 출석 세션 응답 DTO */
@Getter
@Builder
@AllArgsConstructor
public class SessionResponse {

  private Long id;
  private String title;
  private String description;
  private String groupName;
  private LocalDate sessionDate;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private Integer lateThresholdMinutes;
  private String location;
  private SessionStatus status;
  // NFC 태그 정보 (연결된 경우에만)
  private NfcTagInfo nfcTag;
  private String note;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SessionResponse from(AttendanceSession session) {
    return SessionResponse.builder()
        .id(session.getId())
        .title(session.getTitle())
        .description(session.getDescription())
        .groupName(session.getGroupName())
        .sessionDate(session.getSessionDate())
        .startTime(session.getStartTime())
        .endTime(session.getEndTime())
        .lateThresholdMinutes(session.getLateThresholdMinutes())
        .location(session.getLocation())
        .status(session.getStatus())
        // NfcTag가 null이면 null 반환
        .nfcTag(session.getNfcTag() != null ? NfcTagInfo.from(session.getNfcTag()) : null)
        .note(session.getNote())
        .createdBy(session.getCreatedBy())
        .createdAt(session.getCreatedAt())
        .updatedAt(session.getUpdatedAt())
        .build();
  }

  /** 세션 응답에 포함되는 NFC 태그 요약 정보 */
  @Getter
  @Builder
  @AllArgsConstructor
  public static class NfcTagInfo {
    private Long id;
    private String uid;
    private String name;
    private String location;

    public static NfcTagInfo from(com.attendance.domain.nfc.entity.NfcTag nfcTag) {
      return NfcTagInfo.builder()
          .id(nfcTag.getId())
          .uid(nfcTag.getUid())
          .name(nfcTag.getName())
          .location(nfcTag.getLocation())
          .build();
    }
  }
}
