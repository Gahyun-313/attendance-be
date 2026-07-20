package com.attendance.domain.session.service;

import com.attendance.domain.attendance.service.AttendanceService;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.repository.NfcTagRepository;
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

/** 출석 세션 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

  private final SessionRepository sessionRepository;
  private final NfcTagRepository nfcTagRepository;
  private final AttendanceService attendanceService;

  /** 세션 생성 */
  @Transactional
  public SessionResponse createSession(SessionRequest request, Long createdBy) {
    // NFC 태그 연결 (선택)
    NfcTag nfcTag = resolveNfcTag(request.getNfcTagId());

    AttendanceSession session = request.toEntity(createdBy, nfcTag);
    return SessionResponse.from(sessionRepository.save(session));
  }

  /** 세션 목록 조회 (페이징) - 상태/세션명으로 필터링 */
  public Page<SessionResponse> getSessions(
      SessionStatus status, String keyword, Pageable pageable) {
    if (status != null && keyword != null) {
      return sessionRepository
          .findByStatusAndTitleContaining(status, keyword, pageable)
          .map(SessionResponse::from);
    } else if (status != null) {
      return sessionRepository.findByStatus(status, pageable).map(SessionResponse::from);
    } else if (keyword != null) {
      return sessionRepository.findByTitleContaining(keyword, pageable).map(SessionResponse::from);
    }
    return sessionRepository.findAll(pageable).map(SessionResponse::from);
  }

  /** 세션 상세 조회 */
  public SessionResponse getSession(Long sessionId) {
    return SessionResponse.from(findSessionById(sessionId));
  }

  /** 세션 수정 */
  @Transactional
  public SessionResponse updateSession(Long sessionId, SessionRequest request) {
    AttendanceSession session = findSessionById(sessionId);

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

  /** 세션 삭제 */
  @Transactional
  public void deleteSession(Long sessionId) {
    if (!sessionRepository.existsById(sessionId)) {
      throw new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND);
    }
    sessionRepository.deleteById(sessionId);
  }

  /**
   * 세션 시작 - SCHEDULED → ACTIVE, 대상 그룹 학생 전원에게 WAITING 레코드 사전 생성 - 동일 NFC 태그를 사용하는 다른 세션이 이미 ACTIVE면
   * 시작 차단 (체크인 시 태그→세션 역추적이 모호해지는 것을 방지, findActiveSessionsByNfcTagId 참고)
   */
  @Transactional
  public SessionResponse startSession(Long sessionId) {
    AttendanceSession session = findSessionById(sessionId);

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

  /** 세션 종료 - ACTIVE → COMPLETED, 남은 WAITING 레코드를 결석(ABSENT)으로 일괄 처리 */
  @Transactional
  public SessionResponse closeSession(Long sessionId) {
    AttendanceSession session = findSessionById(sessionId);
    session.close();
    attendanceService.markAbsentForRemainingWaiting(sessionId);
    return SessionResponse.from(session);
  }

  /** 세션 취소 */
  @Transactional
  public SessionResponse cancelSession(Long sessionId) {
    AttendanceSession session = findSessionById(sessionId);
    session.cancel();
    return SessionResponse.from(session);
  }

  /** 현재 활성 세션 목록 조회 */
  public List<SessionResponse> getActiveSessions() {
    return sessionRepository.findActiveSessions().stream().map(SessionResponse::from).toList();
  }

  // ------------------------------------------------
  // 내부 유틸
  // ------------------------------------------------

  /** 세션 조회 공통 메서드 */
  private AttendanceSession findSessionById(Long sessionId) {
    return sessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND));
  }

  /** NFC 태그 ID로 태그 조회 (null이면 null 반환) */
  private NfcTag resolveNfcTag(Long nfcTagId) {
    if (nfcTagId == null) return null;
    return nfcTagRepository
        .findById(nfcTagId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
  }
}
