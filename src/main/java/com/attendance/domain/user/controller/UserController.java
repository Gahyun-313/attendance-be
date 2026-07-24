package com.attendance.domain.user.controller;

import com.attendance.domain.user.dto.ChangePasswordRequest;
import com.attendance.domain.user.dto.CreateUserRequest;
import com.attendance.domain.user.dto.UserResponse;
import com.attendance.domain.user.dto.UserUpdateRequest;
import com.attendance.domain.user.service.UserService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 사용자 관리 API Controller */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  /** 학생 계정 생성 POST /api/users - ADMIN 전용 - 기존 /api/auth/signup 이관 */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<UserResponse>> createUser(
      @Valid @RequestBody CreateUserRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.createUser(request, userDetails.getOrganizationId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "학생 계정이 생성되었습니다"));
  }

  /** 학생 목록 조회 GET /api/users - ADMIN 전용 - groupName: 그룹 필터링 (선택) - keyword: 학번/이름 검색 (선택) */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
      @RequestParam(required = false) String groupName,
      @RequestParam(required = false) String keyword,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<UserResponse> response =
        userService.getUsers(groupName, keyword, userDetails.getOrganizationId(), pageable);
    return ResponseEntity.ok(ApiResponse.success(response, "사용자 목록 조회 성공"));
  }

  /**
   * 존재하는 그룹명 목록 조회 GET /api/users/groups - ADMIN 전용 - 세션 생성 시 그룹 선택 드롭다운 등에 활용 - 고정 경로 세그먼트라 GET
   * /api/users/{userId}와 충돌 없음 (Spring이 더 구체적인 패턴을 우선 매칭)
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/groups")
  public ResponseEntity<ApiResponse<List<String>>> getGroups(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    List<String> response = userService.getGroups(userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "그룹 목록 조회 성공"));
  }

  /** 사용자 상세 조회 GET /api/users/{userId} - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{userId}")
  public ResponseEntity<ApiResponse<UserResponse>> getUser(
      @PathVariable Long userId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.getUser(userId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "사용자 조회 성공"));
  }

  /** 사용자 정보 수정 PUT /api/users/{userId} - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{userId}")
  public ResponseEntity<ApiResponse<UserResponse>> updateUser(
      @PathVariable Long userId, @Valid @RequestBody UserUpdateRequest request) {
    UserResponse response = userService.updateUser(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "사용자 정보가 수정되었습니다"));
  }

  /** 사용자 삭제(비활성화) DELETE /api/users/{userId} - ADMIN 전용 - 연관 데이터 보존을 위해 물리 삭제가 아닌 비활성화 처리 */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{userId}")
  public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
    userService.deleteUser(userId);
    return ResponseEntity.ok(ApiResponse.success("사용자가 비활성화되었습니다"));
  }

  /** 내 정보 조회 GET /api/users/me - 본인 전용 (STUDENT/ADMIN 공통) */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    UserResponse response = userService.getMyInfo(userDetails.getUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "내 정보 조회 성공"));
  }

  /** 비밀번호 변경 PATCH /api/users/me/password - 본인 전용 (STUDENT/ADMIN 공통) */
  @PatchMapping("/me/password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(userDetails.getUserId(), request);
    return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다"));
  }
}
