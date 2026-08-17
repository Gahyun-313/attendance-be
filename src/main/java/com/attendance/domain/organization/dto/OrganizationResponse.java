package com.attendance.domain.organization.dto;

import com.attendance.domain.organization.entity.Organization;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 단체 정보 + 출석 정책 응답 DTO - GET/PUT /api/organization/me (ADMIN) * */
@Getter
@Builder
@AllArgsConstructor
public class OrganizationResponse {
  private Long id;
  private String name;
  private String code;
  private Boolean active;
  private Boolean autoAbsentEnabled;
  private Boolean nfcLocationValidationEnabled;
  private Integer defaultAttendanceGraceMinutes;
  private Integer defaultLateThresholdMinutes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static OrganizationResponse from(Organization organization) {
    return OrganizationResponse.builder()
        .id(organization.getId())
        .name(organization.getName())
        .code(organization.getCode())
        .active(organization.getActive())
        .autoAbsentEnabled(organization.getAutoAbsentEnabled())
        .nfcLocationValidationEnabled(organization.getNfcLocationValidationEnabled())
        .defaultAttendanceGraceMinutes(organization.getDefaultAttendanceGraceMinutes())
        .defaultLateThresholdMinutes(organization.getDefaultLateThresholdMinutes())
        .createdAt(organization.getCreatedAt())
        .updatedAt(organization.getUpdatedAt())
        .build();
  }
}
