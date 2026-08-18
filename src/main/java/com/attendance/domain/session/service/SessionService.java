package com.attendance.domain.session.service;

import com.attendance.domain.attendance.service.AttendanceService;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.dto.SessionRequest;
import com.attendance.domain.session.dto.SessionResponse;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 출석 세션 비즈니스 로직 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

  private final SessionRepository sessionRepository;
  private final NfcTagRepository nfcTagRepository;
  private final AttendanceService attendanceService;
  private final OrganizationRepository organizationRepository;

  /** 세션 생성. organizationId는 요청 바디로 받지 않고 생성한 관리자의 인증 정보에서 가져온다. */
  @Transactional
  public SessionResponse createSession(
      SessionRequest request, Long createdBy, Long organizationId) {
    // 선택 항목인 NFC 태그 연결
    NfcTag nfcTag = resolveNfcTag(request.getNfcTagId());

    AttendanceSession session = request.toEntity(createdBy, nfcTag, organizationId);
    return SessionResponse.from(sessionRepository.save(session));
  }

  /** 세션 목록 페이징 조회. 상태/키워드 조합에 따라 조회 메서드를 분기하고, organizationId로 단체 격리 */
  public Page<SessionResponse> getSessions(
      SessionStatus status, String keyword, Long organizationId, Pageable pageable) {
    if (status != null && keyword != null) {
      return sessionRepository
          .findByOrganizationIdAndStatusAndTitleContaining(
              organizationId, status, keyword, pageable)
          .map(SessionResponse::from);
    } else if (status != null) {
      return sessionRepository
          .findByOrganizationIdAndStatus(organizationId, status, pageable)
          .map(SessionResponse::from);
    } else if (keyword != null) {
      return sessionRepository
          .findByOrganizationIdAndTitleContaining(organizationId, keyword, pageable)
          .map(SessionResponse::from);
    }
    return sessionRepository
        .findByOrganizationId(organizationId, pageable)
        .map(SessionResponse::from);
  }

  /** 세션 상세 조회. 다른 단체 소속이면 404로 존재 자체를 숨긴다. */
  public SessionResponse getSession(Long sessionId, Long organizationId) {
    return SessionResponse.from(findSessionByIdAndOrganization(sessionId, organizationId));
  }

  /** 세션 수정. 다른 단체 세션이면 404로 존재 자체를 숨긴다. */
  @Transactional
  public SessionResponse updateSession(
      Long sessionId, SessionRequest request, Long organizationId) {
    AttendanceSession session = findSessionByIdAndOrganization(sessionId, organizationId);

    // 완료/취소된 세션은 수정 불가
    if (session.getStatus() == SessionStatus.COMPLETED
        || session.getStatus() == SessionStatus.CANCELED) {
      throw new BusinessException(ErrorCode.SESSION_ALREADY_CLOSED);
    }

    NfcTag nfcTag = resolveNfcTag(request.getNfcTagId());

    // 도메인 메서드로 수정
    session.update(request, nfcTag);
    return SessionResponse.from(session);
  }

  /** 세션 삭제. 다른 단체 세션이면 404로 존재 자체를 숨긴다. */
  @Transactional
  public void deleteSession(Long sessionId, Long organizationId) {
    findSessionByIdAndOrganization(sessionId, organizationId);
    sessionRepository.deleteById(sessionId);
  }

  /**
   * 세션 시작(SCHEDULED -> ACTIVE)
   * 같은 NFC 태그를 쓰는 다른 세션이 이미 ACTIVE면, 체크인 시 태그로 세션을 역추적하는 게 모호해지므로 시작을 막는다.
   * 세션을 시작한 뒤 대상 그룹 학생 전원에게 WAITING 레코드 미리 생성
   */
  @Transactional
  public SessionResponse startSession(Long sessionId, Long organizationId) {
    AttendanceSession session = findSessionByIdAndOrganization(sessionId, organizationId);

    // 같은 NFC 태그를 쓰는 다른 ACTIVE 세션이 있는지 확인
    if (session.getNfcTag() != null) {
      boolean tagAlreadyInUse =
          sessionRepository
              .findByNfcTagIdAndStatus(session.getNfcTag().getId(), SessionStatus.ACTIVE)
              .stream()
              .anyMatch(other -> !other.getId().equals(sessionId));
      if (tagAlreadyInUse) {
        throw new BusinessException(ErrorCode.NFC_TAG_ALREADY_IN_USE);
      }
    }

    session.start();
    attendanceService.initializeWaitingRecords(session);
    return SessionResponse.from(session);
  }

  /** 세션 종료(ACTIVE -> COMPLETED). 단체 설정에 따라 남은 WAITING 레코드를 결석(ABSENT)으로 일괄 처리 */
  @Transactional
  public SessionResponse closeSession(Long sessionId, Long organizationId) {
    AttendanceSession session = findSessionByIdAndOrganization(sessionId, organizationId);
    session.close();
    // 단체 설정(autoAbsentEnable)이 꺼져 있으면 남은 WAITING을 그대로 두고 관리자가 수동으로 처리한다.
    if (isAutoAbsentEnable(organizationId)) {
      attendanceService.markAbsentForRemainingWaiting(sessionId);
    }
    return SessionResponse.from(session);
  }

  /** 세션 취소. 다른 단체 세션이면 404로 존재 자체를 숨긴다. */
  @Transactional
  public SessionResponse cancelSession(Long sessionId, Long organizationId) {
    AttendanceSession session = findSessionByIdAndOrganization(sessionId, organizationId);
    session.cancel();
    return SessionResponse.from(session);
  }

  /** 현재 활성 세션 목록 조회. organizationId로 단체 격리 */
  public List<SessionResponse> getActiveSessions(Long organizationId) {
    return sessionRepository.findActiveSessions(organizationId).stream()
        .map(SessionResponse::from)
        .toList();
  }

  // 내부 유틸

  /** 세션 조회 */
  private AttendanceSession findSessionById(Long sessionId) {
    return sessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND));
  }

  /** 세션을 조회하고 소속 단체 검증. 수정/삭제/시작/종료/취소 전에 호출해 다른 단체 세션 조작을 404로 차단한다. */
  private AttendanceSession findSessionByIdAndOrganization(Long sessionId, Long organizationId) {
    AttendanceSession session = findSessionById(sessionId);
    if (!session.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND);
    }
    return session;
  }

  /** 단체의 "결석 자동 처리" 정책 조회. 단체를 찾지 못하면 기본값 true를 반환한다. */
  private boolean isAutoAbsentEnable(Long organizationId) {
    return organizationRepository
        .findById(organizationId)
        .map(Organization::getAutoAbsentEnabled)
        .orElse(true);
  }

  /** NFC 태그 ID로 태그 조회. nfcTagId가 null이면 null을 반환한다. */
  private NfcTag resolveNfcTag(Long nfcTagId) {
    if (nfcTagId == null) return null;
    return nfcTagRepository
        .findById(nfcTagId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
  }
}
