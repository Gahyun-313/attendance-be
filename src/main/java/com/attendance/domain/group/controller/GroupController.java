package com.attendance.domain.group.controller;

import com.attendance.domain.group.dto.GroupRequest;
import com.attendance.domain.group.dto.GroupResponse;
import com.attendance.domain.group.service.GroupService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 그룹 관리 API 제공 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

  private final GroupService groupService;

  /** 그룹 생성(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
      @Valid @RequestBody GroupRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    GroupResponse response = groupService.createGroup(request, userDetails.getOrganizationId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "그룹이 생성되었습니다"));
  }

  /** 그룹 목록 조회(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<ApiResponse<List<GroupResponse>>> getGroups(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    List<GroupResponse> response = groupService.getGroups(userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "그룹 목록 조회 성공"));
  }

  /** 그룹 수정(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{groupId}")
  public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(
      @PathVariable Long groupId,
      @Valid @RequestBody GroupRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    GroupResponse response =
        groupService.updateGroup(groupId, request, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "그룹이 수정되었습니다"));
  }

  /** 그룹 삭제(ADMIN 전용) */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{groupId}")
  public ResponseEntity<ApiResponse<Void>> deleteGroup(
      @PathVariable Long groupId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    groupService.deleteGroup(groupId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success("그룹이 삭제되었습니다"));
  }
}
