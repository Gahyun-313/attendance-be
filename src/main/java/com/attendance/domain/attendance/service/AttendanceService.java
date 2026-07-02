package com.attendance.domain.attendance.service;

import com.attendance.domain.attendance.dto.AttendanceDashboardResponse;
import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.dto.AttendanceStatusUpdateRequest;
import com.attendance.domain.attendance.dto.CheckInRequest;
import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.user.entity.User;
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

    /**
     * 출석 체크인 (STUDENT)
     * 흐름: NFC UID 검증 → 활성 세션 역추적 → 중복 방지 → 지각 판정 → 레코드 저장
     *      + 태그 사용시각/사용자 첫 출석시각 갱신
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

        // 3. 중복 출석 방지 (애플리케이션 레벨 1차 방어, 최종 보장은 DB user_id+session_id Unique 제약)
        //    동시 요청 동시성 제어(분산 락)는 Day 6 예정
        if (attendanceRepository.existsByUserIdAndSessionId(userId, session.getId())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_ATTENDANCE);
        }

        // 4. 출석/지각 판정 - session.isLate() 기준
        LocalDateTime checkInTime = LocalDateTime.now();
        AttendanceStatus status =
                session.isLate(checkInTime) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        // 5. 출석 레코드 생성 (스캔 당시 태그 위치를 함께 보존)
        AttendanceRecord record =
                AttendanceRecord.builder()
                        .userId(userId)
                        .sessionId(session.getId())
                        .status(status)
                        .checkInTime(checkInTime)
                        .nfcTagUid(nfcTag.getUid())
                        .nfcLocation(nfcTag.getLocation())
                        .build();
        AttendanceRecord saved = attendanceRepository.save(record);

        // 6. 부가 처리 - 더티 체킹으로 반영됨 (같은 트랜잭션 내 managed 엔티티)
        nfcTag.markUsed(); // 태그 마지막 사용시각 갱신
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        user.recordFirstAttendanceIfAbsent(checkInTime); // 사용자 첫 출석 시각 기록

        return AttendanceResponse.from(saved, user);
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
                .map(record -> AttendanceResponse.from(record, user));
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
                .map(record -> AttendanceResponse.from(record, userMap.get(record.getUserId())))
                .toList();
    }

    /** 출석 상태 수동 수정 (ADMIN) - modifiedBy는 인증된 관리자 이름 */
    @Transactional
    public AttendanceResponse updateStatus(
            Long attendanceId, AttendanceStatusUpdateRequest request, String modifiedBy) {
        AttendanceRecord record =
                attendanceRepository
                        .findById(attendanceId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));

        // 도메인 메서드로 상태 변경 (누가/왜 바꿨는지 함께 기록)
        record.modifyStatus(request.getStatus(), modifiedBy, request.getModifyReason());

        User user = userRepository.findById(record.getUserId()).orElse(null);
        return AttendanceResponse.from(record, user);
    }

    /** 출석 기록 삭제 (ADMIN) */
    @Transactional
    public void deleteAttendance(Long attendanceId) {
        if (!attendanceRepository.existsById(attendanceId)) {
            throw new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
        attendanceRepository.deleteById(attendanceId);
    }

    /** 세션별 출석 대시보드 (ADMIN) - 상태별 레코드 수 집계 */
    public AttendanceDashboardResponse getSessionDashboard(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND);
        }
        long total = attendanceRepository.countBySessionId(sessionId);
        long present = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.PRESENT);
        long late = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.LATE);
        long absent = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.ABSENT);
        long waiting = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);

        return AttendanceDashboardResponse.of(sessionId, total, present, late, absent, waiting);
    }
}