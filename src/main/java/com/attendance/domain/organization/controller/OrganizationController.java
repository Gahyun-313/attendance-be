package com.attendance.domain.organization.controller;

import com.attendance.domain.organization.dto.OrganizationResponse;
import com.attendance.domain.organization.dto.OrganizationUpdateRequest;
import com.attendance.domain.organization.service.OrganizationService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN 본인 소속 단체 정보 + 출석 정책 API Controller */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

  private final OrganizationService organizationService;

  /** 단체 정보 + 출석 정책 조회 GET /api/organizations/me (ADMIN) */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<OrganizationResponse>> getMyOrganization(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    OrganizationResponse response =
        organizationService.getMyOrganization(userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "단체 정보 조회 성공"));
  }

  /** 단체 정보 + 출석 정책 수정 PUT /api/organizations/me (ADMIN) */
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/me")
  public ResponseEntity<ApiResponse<OrganizationResponse>> updateMyOrganization(
      @Valid @RequestBody OrganizationUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    OrganizationResponse response =
        organizationService.updateMyOrganization(userDetails.getOrganizationId(), request);
    return ResponseEntity.ok(ApiResponse.success(response, "단체 정보가 수정되었습니다"));
  }
}
