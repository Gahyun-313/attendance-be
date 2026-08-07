package com.attendance.domain.nfc.controller;

import com.attendance.domain.nfc.dto.NfcTagRequest;
import com.attendance.domain.nfc.dto.NfcTagResponse;
import com.attendance.domain.nfc.dto.NfcTagUpdateRequest;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import com.attendance.domain.nfc.service.NfcTagService;
import com.attendance.global.response.ApiResponse;
import com.attendance.global.security.CustomUserDetails;
import jakarta.validation.Valid;
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

/** NFC 태그 관리 API Controller */
@RestController
@RequestMapping("/api/nfc-tags")
@RequiredArgsConstructor
public class NfcTagController {

  private final NfcTagService nfcTagService;

  /** NFC 태그 등록 POST /api/nfc-tags - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<NfcTagResponse>> createNfcTag(
      @Valid @RequestBody NfcTagRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    NfcTagResponse response = nfcTagService.createNfcTag(request, userDetails.getOrganizationId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "NFC 태그가 등록되었습니다"));
  }

  /** NFC 태그 목록 조회 (페이징) GET /api/nfc-tags - 전체: status 파라미터 없음 - 상태별: ?status=ACTIVE */
  @GetMapping
  public ResponseEntity<ApiResponse<Page<NfcTagResponse>>> getNfcTags(
      @RequestParam(required = false) NfcTagStatus status,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<NfcTagResponse> response =
        status != null
            ? nfcTagService.getNfcTagsByStatus(status, userDetails.getOrganizationId(), pageable)
            : nfcTagService.getAllNfcTags(userDetails.getOrganizationId(), pageable);
    return ResponseEntity.ok(ApiResponse.success(response, "NFC 태그 목록 조회 성공"));
  }

  /** NFC 태그 상세 조회 GET /api/nfc-tags/{tagId} */
  @GetMapping("/{tagId}")
  public ResponseEntity<ApiResponse<NfcTagResponse>> getNfcTag(
      @PathVariable Long tagId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    NfcTagResponse response = nfcTagService.getNfcTagById(tagId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "NFC 태그 조회 성공"));
  }

  /** UID로 NFC 태그 조회 GET /api/nfc-tags/uid/{uid} - AOS 앱에서 NFC 스캔 후 태그 검증에 사용 */
  @GetMapping("/uid/{uid}")
  public ResponseEntity<ApiResponse<NfcTagResponse>> getNfcTagByUid(@PathVariable String uid) {
    NfcTagResponse response = nfcTagService.getNfcTagByUid(uid);
    return ResponseEntity.ok(ApiResponse.success(response, "NFC 태그 조회 성공"));
  }

  /** NFC 태그 수정 PUT /api/nfc-tags/{tagId} - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{tagId}")
  public ResponseEntity<ApiResponse<NfcTagResponse>> updateNfcTag(
      @PathVariable Long tagId,
      @Valid @RequestBody NfcTagUpdateRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    NfcTagResponse response =
        nfcTagService.updateNfcTag(tagId, request, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "NFC 태그가 수정되었습니다"));
  }

  /** NFC 태그 활성화 POST /api/nfc-tags/{tagId}/activate - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{tagId}/activate")
  public ResponseEntity<ApiResponse<NfcTagResponse>> activateNfcTag(
      @PathVariable Long tagId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    NfcTagResponse response = nfcTagService.activateNfcTag(tagId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "NFC 태그가 활성화되었습니다"));
  }

  /** NFC 태그 비활성화 POST /api/nfc-tags/{tagId}/deactivate - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{tagId}/deactivate")
  public ResponseEntity<ApiResponse<NfcTagResponse>> deactivateNfcTag(
      @PathVariable Long tagId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    NfcTagResponse response =
        nfcTagService.deactivateNfcTag(tagId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success(response, "NFC 태그가 비활성화되었습니다"));
  }

  /** NFC 태그 삭제 DELETE /api/nfc-tags/{tagId} - ADMIN 전용 */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{tagId}")
  public ResponseEntity<ApiResponse<Void>> deleteNfcTag(
      @PathVariable Long tagId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    nfcTagService.deleteNfcTag(tagId, userDetails.getOrganizationId());
    return ResponseEntity.ok(ApiResponse.success("NFC 태그가 삭제되었습니다"));
  }

  /** NFC 태그 검증 POST /api/nfc-tags/validate - AOS 앱에서 출석 체크 전 태그 유효성 검증에 사용 */
  @PostMapping("/validate")
  public ResponseEntity<ApiResponse<Boolean>> validateNfcTag(@RequestParam String uid) {
    boolean isValid = nfcTagService.isValidNfcTag(uid);
    return ResponseEntity.ok(ApiResponse.success(isValid, "NFC 태그 검증 완료"));
  }
}
