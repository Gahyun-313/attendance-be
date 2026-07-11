package com.attendance.domain.attendance.service;

import com.attendance.domain.attendance.dto.AttendanceDashboardResponse;
import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.dto.AttendanceStatusUpdateRequest;
import com.attendance.domain.attendance.dto.CheckInRequest;
import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.event.AttendanceCheckedInEvent;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 출석 기록 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final NfcTagRepository nfcTagRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher; // 체크인 완료 후 실시간 푸시 트리거용 (Day4 Phase2)

    /**
     * 출석 체크인 (STUDENT)
     * 흐름: NFC UID 검증 → 활성 세션 역추적 → 지각 판정 → 레코드 갱신/생성
     *      + 태그 사용시각/사용자 첫 출석시각 갱신
     * - 세션 시작 시 사전 생성된 WAITING 레코드가 있으면 그걸 갱신하고, 없으면(그룹 미지정 세션 등) 새로 생성한다.
     * - 이미 PRESENT/LATE/ABSENT로 처리된 레코드가 있으면 중복 출석으로 간주해 예외.
     */
    @Transactional
    public AttendanceResponse checkIn(Long userId, CheckInRequest request) {
        // 1. NFC 태그 조회 및 활성 상태 확인
        NfcTag nfcTag =
                nfcTagRepository
                        .findByUid(request.getNfcTagUid())
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
        if (!nfcTag.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_NFC_TAG);
        }

        // 2. 태그에 연결된 현재 활성 세션 역추적 (요청 바디엔 sessionId 없음)
        //    정상 운영 시 활성 세션은 1개, 다수면 첫 번째 사용
        AttendanceSession session =
                sessionRepository.findActiveSessionsByNfcTagId(nfcTag.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_ACTIVE));

        // 3. 출석/지각 판정 - session.isLate() 기준
        LocalDateTime checkInTime = LocalDateTime.now();
        AttendanceStatus status =
                session.isLate(checkInTime) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        // 4. 기존 레코드(WAITING 사전 등록분) 갱신, 없으면 신규 생성
        //    이미 PRESENT/LATE/ABSENT면 중복 출석 (애플리케이션 레벨 1차 방어, 최종 보장은 DB Unique 제약)
        //    동시 요청 동시성 제어(분산 락)는 Day 6 예정
        AttendanceRecord attendanceRecord =
                attendanceRepository
                        .findByUserIdAndSessionId(userId, session.getId())
                        .map(existing -> {
                            if (existing.getStatus() != AttendanceStatus.WAITING) {
                                throw new DuplicateException(ErrorCode.DUPLICATE_ATTENDANCE);
                            }
                            existing.checkIn(status, checkInTime, nfcTag.getUid(), nfcTag.getLocation());
                            return existing;
                        })
                        .orElseGet(
                                () ->
                                        attendanceRepository.save(
                                                AttendanceRecord.builder()
                                                        .userId(userId)
                                                        .sessionId(session.getId())
                                                        .status(status)
                                                        .checkInTime(checkInTime)
                                                        .nfcTagUid(nfcTag.getUid())
                                                        .nfcLocation(nfcTag.getLocation())
                                                        .build()));

        // 5. 부가 처리 - 더티 체킹으로 반영됨 (같은 트랜잭션 내 managed 엔티티)
        nfcTag.markUsed(); // 태그 마지막 사용시각 갱신
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        user.recordFirstAttendanceIfAbsent(checkInTime); // 사용자 첫 출석 시각 기록

        // 6. 실시간 푸시 트리거 - 이 시점은 아직 트랜잭션 커밋 전이라 값을 직접 싣지 않고 PK만 이벤트로 발행한다.
        //    실제 조회/전송은 AttendanceEventListener가 트랜잭션이 커밋된 뒤(AFTER_COMMIT)에 수행한다.
        eventPublisher.publishEvent(
                new AttendanceCheckedInEvent(session.getId(), attendanceRecord.getId()));

        return AttendanceResponse.from(attendanceRecord, user);
    }

    /** 내 출석 기록 조회 (STUDENT, 페이징) */
    public Page<AttendanceResponse> getMyAttendances(Long userId, Pageable pageable) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        // 본인 기록이므로 User는 1회만 조회해 재사용
        return attendanceRepository
                .findByUserId(userId, pageable)
                .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, user));
    }

    /** 세션별 출석 현황 조회 (ADMIN) */
    public List<AttendanceResponse> getSessionAttendances(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND);
        }
        List<AttendanceRecord> records = attendanceRepository.findBySessionId(sessionId);

        // N+1 방지: 레코드의 userId를 모아 User를 한 번에 조회 후 Map으로 매핑
        List<Long> userIds = records.stream().map(AttendanceRecord::getUserId).distinct().toList();
        Map<Long, User> userMap =
                userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        return records.stream()
                .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, userMap.get(attendanceRecord.getUserId())))
                .toList();
    }

    /** 출석 상태 수동 수정 (ADMIN) - modifiedBy는 인증된 관리자 이름 */
    @Transactional
    public AttendanceResponse updateStatus(
            Long attendanceId, AttendanceStatusUpdateRequest request, String modifiedBy) {
        AttendanceRecord attendanceRecord =
                attendanceRepository
                        .findById(attendanceId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));

        // 도메인 메서드로 상태 변경 (누가/왜 바꿨는지 함께 기록)
        attendanceRecord.modifyStatus(request.getStatus(), modifiedBy, request.getModifyReason());

        User user = userRepository.findById(attendanceRecord.getUserId()).orElse(null);
        return AttendanceResponse.from(attendanceRecord, user);
    }

    /** 출석 기록 삭제 (ADMIN) */
    @Transactional
    public void deleteAttendance(Long attendanceId) {
        if (!attendanceRepository.existsById(attendanceId)) {
            throw new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
        attendanceRepository.deleteById(attendanceId);
    }

    /**
     * 세션별 출석 대시보드 (ADMIN) - 상태별 레코드 수 + 대상자 수(targetCount) 집계
     * - targetCount: 세션 groupName 기준 STUDENT 수. 그룹 미지정 세션은 totalRecords로 근사(AttendanceDashboardResponse에서 처리)
     */
    public AttendanceDashboardResponse getSessionDashboard(Long sessionId) {
        AttendanceSession session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND));

        long total = attendanceRepository.countBySessionId(sessionId);
        long present = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.PRESENT);
        long late = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.LATE);
        long absent = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.ABSENT);
        long waiting = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);
        long targetCount =
                session.getGroupName() != null
                        ? userRepository.countByRoleAndGroupName(UserRole.STUDENT, session.getGroupName())
                        : 0L;

        return AttendanceDashboardResponse.of(sessionId, targetCount, total, present, late, absent, waiting);
    }

    /**
     * 출석 레코드 단건 상세 조회 - AttendanceEventListener가 체크인 커밋 후 실시간 푸시 페이로드를 만들 때 사용한다.
     * (이벤트 발행 시점 값이 아니라 커밋이 확정된 뒤의 최신 상태를 다시 읽기 위함, 섹션 12 참고)
     */
    public AttendanceResponse getAttendanceRecord(Long attendanceId) {
        AttendanceRecord attendanceRecord =
                attendanceRepository
                        .findById(attendanceId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));
        User user = userRepository.findById(attendanceRecord.getUserId()).orElse(null);
        return AttendanceResponse.from(attendanceRecord, user);
    }

    /**
     * 세션 시작 시 대상 그룹 학생 전원에게 WAITING 레코드 사전 생성 (SessionService.startSession에서 호출)
     * - 그룹 미지정 세션(groupName == null)은 사전 등록 대상이 없으므로 스킵
     * - 이미 레코드가 있는 사용자는 건너뜀 (재시작 등으로 중복 호출되어도 안전)
     */
    @Transactional
    public void initializeWaitingRecords(AttendanceSession session) {
        if (session.getGroupName() == null) {
            return;
        }
        List<User> targets =
                userRepository.findByRoleAndGroupName(UserRole.STUDENT, session.getGroupName());
        for (User target : targets) {
            if (!attendanceRepository.existsByUserIdAndSessionId(target.getId(), session.getId())) {
                attendanceRepository.save(
                        AttendanceRecord.builder()
                                .userId(target.getId())
                                .sessionId(session.getId())
                                .status(AttendanceStatus.WAITING)
                                .build());
            }
        }
    }

    /**
     * 세션 종료 시 아직 체크인하지 않은(WAITING) 레코드를 결석(ABSENT)으로 일괄 처리
     * (SessionService.closeSession에서 호출)
     */
    @Transactional
    public void markAbsentForRemainingWaiting(Long sessionId) {
        List<AttendanceRecord> waitingRecords =
                attendanceRepository.findBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);
        for (AttendanceRecord attendanceRecord : waitingRecords) {
            attendanceRecord.modifyStatus(AttendanceStatus.ABSENT, "SYSTEM", "세션 종료 시 자동 결석 처리");
        }
    }
}