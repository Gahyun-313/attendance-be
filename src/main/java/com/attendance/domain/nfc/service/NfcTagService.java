package com.attendance.domain.nfc.service;

import com.attendance.domain.nfc.dto.NfcTagRequest;
import com.attendance.domain.nfc.dto.NfcTagResponse;
import com.attendance.domain.nfc.dto.NfcTagUpdateRequest;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NFC 태그 비즈니스 로직 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NfcTagService {

  private final NfcTagRepository nfcTagRepository;

  /** NFC 태그 등록. organizationId는 등록을 요청한 관리자의 단체로 고정해 다른 단체 태그 노출을 막는다. */
  @Transactional
  public NfcTagResponse createNfcTag(NfcTagRequest request, Long organizationId) {
    if (nfcTagRepository.existsByUid(request.getUid())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_NFC_UID);
    }
    NfcTag savedNfcTag = nfcTagRepository.save(request.toEntity(organizationId));
    return NfcTagResponse.from(savedNfcTag);
  }

  /** NFC 태그 ID 조회 */
  public NfcTagResponse getNfcTagById(Long id, Long organizationId) {
    return NfcTagResponse.from(findNfcTagByIdAndOrganization(id, organizationId));
  }

  /** NFC 태그 UID 조회 */
  public NfcTagResponse getNfcTagByUid(String uid) {
    NfcTag nfcTag =
        nfcTagRepository
            .findByUid(uid)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
    return NfcTagResponse.from(nfcTag);
  }

  /** 단체 내 모든 NFC 태그 페이징 조회 */
  public Page<NfcTagResponse> getAllNfcTags(Long organizationId, Pageable pageable) {
    return nfcTagRepository
        .findByOrganizationId(organizationId, pageable)
        .map(NfcTagResponse::from);
  }

  /** 단체+상태별 NFC 태그 페이징 조회 */
  public Page<NfcTagResponse> getNfcTagsByStatus(
      NfcTagStatus status, Long organizationId, Pageable pageable) {
    return nfcTagRepository
        .findByOrganizationIdAndStatus(organizationId, status, pageable)
        .map(NfcTagResponse::from);
  }

  /** 이름 또는 위치로 NFC 태그 검색 */
  public Page<NfcTagResponse> searchNfcTags(
      String keyword, Long organizationId, Pageable pageable) {
    return nfcTagRepository
        .findByOrganizationIdAndNameContainingOrLocationContaining(
            organizationId, keyword, keyword, pageable)
        .map(NfcTagResponse::from);
  }

  /** NFC 태그 정보 수정 */
  @Transactional
  public NfcTagResponse updateNfcTag(Long id, NfcTagUpdateRequest request, Long organizationId) {
    NfcTag nfcTag = findNfcTagByIdAndOrganization(id, organizationId);
    nfcTag.updateInfo(request.getName(), request.getDescription(), request.getLocation());
    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 활성화 */
  @Transactional
  public NfcTagResponse activateNfcTag(Long id, Long organizationId) {
    NfcTag nfcTag = findNfcTagByIdAndOrganization(id, organizationId);
    nfcTag.activate();
    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 비활성화 */
  @Transactional
  public NfcTagResponse deactivateNfcTag(Long id, Long organizationId) {
    NfcTag nfcTag = findNfcTagByIdAndOrganization(id, organizationId);
    nfcTag.deactivate();
    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 삭제 */
  @Transactional
  public void deleteNfcTag(Long id, Long organizationId) {
    NfcTag nfcTag = findNfcTagByIdAndOrganization(id, organizationId);
    nfcTagRepository.delete(nfcTag);
  }

  /** NFC UID 유효성 검증(출석 체크 시 사용) */
  public boolean isValidNfcTag(String uid) {
    return nfcTagRepository.findByUid(uid).map(NfcTag::isActive).orElse(false);
  }

  /**
   * ID와 organizationId로 NFC 태그를 조회한다. 다른 단체 소속이면 404로 존재 자체를 숨긴다(UserService/SessionService와 동일
   * 패턴).
   */
  private NfcTag findNfcTagByIdAndOrganization(Long id, Long organizationId) {
    NfcTag nfcTag =
        nfcTagRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
    if (!nfcTag.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND);
    }
    return nfcTag;
  }
}
