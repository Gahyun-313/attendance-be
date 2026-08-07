package com.attendance.domain.nfc.repository;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** NFC 태그 Repository */
public interface NfcTagRepository extends JpaRepository<NfcTag, Long> {

  /** UID로 NFC 태그 조회 - AOS 앱에서 NFC 스캔 시 UID로 태그 검증에 사용 */
  Optional<NfcTag> findByUid(String uid);

  /** UID 존재 여부 확인 - 중복 UID 체크에 사용 */
  boolean existsByUid(String uid);

  /** 단체별 NFC 태그 조회 (페이징) - 관리자 화면 전체 목록 조회에 사용 */
  Page<NfcTag> findByOrganizationId(Long organizationId, Pageable pageable);

  /** 단체 + 상태별 NFC 태그 조회 (페이징) - 관리자 화면에서 활성/비활성 태그 조회에 사용 */
  Page<NfcTag> findByOrganizationIdAndStatus(
      Long organizationId, NfcTagStatus status, Pageable pageable);

  /** 단체 + 이름/위치로 NFC 태그 검색 (페이징) - 관리자 화면에서 태그 검색에 사용 */
  Page<NfcTag> findByOrganizationIdAndNameContainingOrLocationContaining(
      Long organizationId, String name, String location, Pageable pageable);
}
