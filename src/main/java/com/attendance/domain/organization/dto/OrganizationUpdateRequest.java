package com.attendance.domain.organization.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 단체 정보 + 출석 정책 수정 요청 DTO - PUT /api/organizations/me (ADMIN) - 모든 필드는 선택값 (null이면 해당 항목 미변경) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationUpdateRequest {

  @Size(max = 100, message = "단체명은 100자를 초과할 수 없습니다")
  private String name;

  private Boolean autoAbsentEnabled;
  private Boolean nfcLocationValidationEnabled;

  @Min(value = 0, message = "출석 인정 시간은 0 이상이어야 합니다")
  private Integer defaultAttendanceGraceMinutes;

  @Min(value = 0, message = "지각 기준 시각은 0 이상이어야 합니다")
  private Integer defaultLateThresholdMinutes;
}
