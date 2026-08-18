package com.attendance.domain.organization.service;

import com.attendance.domain.organization.dto.OrganizationResponse;
import com.attendance.domain.organization.dto.OrganizationUpdateRequest;
import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADMIN 본인 소속 단체 정보 + 출석 정책 관리 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

  private final OrganizationRepository organizationRepository;

  /** 단체 정보 + 출석 정책 조회 GET /api/organizations/me (ADMIN) */
  public OrganizationResponse getMyOrganization(Long organizationId) {
    return OrganizationResponse.from(findOrganization(organizationId));
  }

  /** 단체 정보 + 출석 정책 수정 PUT /api/organizations/me (ADMIN) */
  @Transactional
  public OrganizationResponse updateMyOrganization(
      Long organizationId, OrganizationUpdateRequest request) {
    Organization organization = findOrganization(organizationId);
    organization.updateInfo(request.getName());
    organization.updatePolicy(
        request.getAutoAbsentEnabled(),
        request.getNfcLocationValidationEnabled(),
        request.getDefaultAttendanceGraceMinutes(),
        request.getDefaultLateThresholdMinutes());
    return OrganizationResponse.from(organization);
  }

  private Organization findOrganization(Long organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
  }
}
