package com.attendance.domain.nfc.service;

import com.attendance.domain.nfc.dto.NfcTagRequest;
import com.attendance.domain.nfc.dto.NfcTagResponse;
import com.attendance.domain.nfc.dto.NfcTagUpdateRequest;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NFC 태그 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NfcTagService {

  private final NfcTagRepository nfcTagRepository;

  /** NFC 태그 등록 */
  @Transactional
  public NfcTagResponse createNfcTag(NfcTagRequest request) {
    // UID 중복 체크
    if (nfcTagRepository.existsByUid(request.getUid())) {
      throw new IllegalArgumentException("이미 등록된 UID입니다: " + request.getUid());
    }

    NfcTag nfcTag = request.toEntity();
    NfcTag savedNfcTag = nfcTagRepository.save(nfcTag);

    return NfcTagResponse.from(savedNfcTag);
  }

  /** NFC 태그 ID로 조회 */
  public NfcTagResponse getNfcTagById(Long id) {
    NfcTag nfcTag =
        nfcTagRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("NFC 태그를 찾을 수 없습니다: " + id));

    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 UID로 조회 */
  public NfcTagResponse getNfcTagByUid(String uid) {
    NfcTag nfcTag =
        nfcTagRepository
            .findByUid(uid)
            .orElseThrow(() -> new IllegalArgumentException("NFC 태그를 찾을 수 없습니다: " + uid));

    return NfcTagResponse.from(nfcTag);
  }

  /** 모든 NFC 태그 조회 (페이징) */
  public Page<NfcTagResponse> getAllNfcTags(Pageable pageable) {
    return nfcTagRepository.findAll(pageable).map(NfcTagResponse::from);
  }

  /** 상태별 NFC 태그 조회 (페이징) */
  public Page<NfcTagResponse> getNfcTagsByStatus(NfcTagStatus status, Pageable pageable) {
    return nfcTagRepository.findByStatus(status, pageable).map(NfcTagResponse::from);
  }

  /** NFC 태그 검색 (이름 또는 위치) */
  public Page<NfcTagResponse> searchNfcTags(String keyword, Pageable pageable) {
    return nfcTagRepository
        .findByNameContainingOrLocationContaining(keyword, keyword, pageable)
        .map(NfcTagResponse::from);
  }

  /** NFC 태그 정보 수정 */
  @Transactional
  public NfcTagResponse updateNfcTag(Long id, NfcTagUpdateRequest request) {
    NfcTag nfcTag =
        nfcTagRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("NFC 태그를 찾을 수 없습니다: " + id));

    nfcTag.updateInfo(request.getName(), request.getDescription(), request.getLocation());

    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 활성화 */
  @Transactional
  public NfcTagResponse activateNfcTag(Long id) {
    NfcTag nfcTag =
        nfcTagRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("NFC 태그를 찾을 수 없습니다: " + id));

    nfcTag.activate();

    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 비활성화 */
  @Transactional
  public NfcTagResponse deactivateNfcTag(Long id) {
    NfcTag nfcTag =
        nfcTagRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("NFC 태그를 찾을 수 없습니다: " + id));

    nfcTag.deactivate();

    return NfcTagResponse.from(nfcTag);
  }

  /** NFC 태그 삭제 */
  @Transactional
  public void deleteNfcTag(Long id) {
    if (!nfcTagRepository.existsById(id)) {
      throw new IllegalArgumentException("NFC 태그를 찾을 수 없습니다: " + id);
    }

    nfcTagRepository.deleteById(id);
  }

  /** NFC UID 유효성 검증 (출석 체크 시 사용) */
  public boolean isValidNfcTag(String uid) {
    return nfcTagRepository.findByUid(uid).map(NfcTag::isActive).orElse(false);
  }
}
