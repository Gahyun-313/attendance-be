package com.attendance.domain.session.dto;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석 세션 생성/수정 요청 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SessionRequest {

  @NotBlank(message = "세션명은 필수입니다")
  @Size(max = 200, message = "세션명은 200자를 초과할 수 없습니다")
  private String title;

  @Size(max = 500, message = "설명은 500자를 초과할 수 없습니다")
  private String description;

  @Size(max = 100, message = "그룹명은 100자를 초과할 수 없습니다")
  private String groupName;

  // 세션 날짜 (null이면 startTime의 날짜로 자동 세팅)
  private LocalDate sessionDate;

  @NotNull(message = "시작 시간은 필수입니다")
  private LocalDateTime startTime;

  @NotNull(message = "종료 시간은 필수입니다")
  private LocalDateTime endTime;

  // 지각 기준 시간(분) - 기본값 10분
  @Positive(message = "지각 기준 시간은 양수여야 합니다")
  private Integer lateThresholdMinutes;

  @Size(max = 100, message = "위치는 100자를 초과할 수 없습니다")
  private String location;

  // 연결할 NFC 태그 ID (선택)
  private Long nfcTagId;

  @Size(max = 500, message = "비고는 500자를 초과할 수 없습니다")
  private String note;

  /**
   * Request DTO를 엔티티로 변환
   *
   * @param createdBy 세션 생성자 userId
   * @param nfcTag 연결할 NfcTag 엔티티 (null 가능)
   * @param organizationId 생성한 관리자가 속한 단체 ID - 클라이언트가 임의로 다른 단체를 지정할 수 없도록 요청 필드가 아닌 파라미터로 받는다
   */
  public AttendanceSession toEntity(Long createdBy, NfcTag nfcTag, Long organizationId) {
    return AttendanceSession.builder()
        .organizationId(organizationId)
        .title(title)
        .description(description)
        .groupName(groupName)
        // sessionDate가 null이면 startTime의 날짜로 자동 세팅
        .sessionDate(sessionDate != null ? sessionDate : startTime.toLocalDate())
        .startTime(startTime)
        .endTime(endTime)
        .lateThresholdMinutes(lateThresholdMinutes != null ? lateThresholdMinutes : 10)
        .location(location)
        .status(SessionStatus.SCHEDULED) // 생성 시 기본값 SCHEDULED
        .nfcTag(nfcTag)
        .note(note)
        .createdBy(createdBy)
        .build();
  }
}
