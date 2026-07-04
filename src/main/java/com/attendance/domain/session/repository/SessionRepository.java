package com.attendance.domain.session.repository;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 출석 세션 Repository */
public interface SessionRepository extends JpaRepository<AttendanceSession, Long> {

    /** 상태별 세션 조회 (페이징) */
    Page<AttendanceSession> findByStatus(SessionStatus status, Pageable pageable);

    /** 그룹별 세션 조회 (페이징) */
    Page<AttendanceSession> findByGroupName(String groupName, Pageable pageable);

    /** 날짜별 세션 조회 */
    List<AttendanceSession> findBySessionDate(LocalDate sessionDate);

    /** 세션명 검색 (페이징) */
    Page<AttendanceSession> findByTitleContaining(String title, Pageable pageable);

    /** 상태 + 세션명 검색 (페이징) */
    Page<AttendanceSession> findByStatusAndTitleContaining(
            SessionStatus status, String title, Pageable pageable);

    /** 현재 활성 세션 조회 - 현재 시각이 startTime ~ endTime 사이이고 ACTIVE 상태인 세션 */
    @Query("""
      SELECT s FROM AttendanceSession s
      WHERE s.status = 'ACTIVE'
      AND s.startTime <= CURRENT_TIMESTAMP
      AND s.endTime >= CURRENT_TIMESTAMP
      """)
    List<AttendanceSession> findActiveSessions();

    /**
     * NFC 태그 ID로 현재 활성 세션 조회 - 학생 체크인 시 태그에 연결된 진행 중 세션 역추적에 사용.
     * 정상 운영 시 동일 태그에 동시에 ACTIVE인 세션은 1개여야 하며(SessionService.startSession에서 검증),
     * 혹시 다수가 잡히는 경우를 대비해 startTime 내림차순 정렬 후 서비스에서 첫 번째(가장 최근 시작된 세션)를 사용한다.
     */
    @Query("""
      SELECT s FROM AttendanceSession s
      WHERE s.nfcTag.id = :nfcTagId
      AND s.status = 'ACTIVE'
      AND s.startTime <= CURRENT_TIMESTAMP
      AND s.endTime >= CURRENT_TIMESTAMP
      ORDER BY s.startTime DESC
      """)
    List<AttendanceSession> findActiveSessionsByNfcTagId(@Param("nfcTagId") Long nfcTagId);

    /**
     * 태그ID + 상태로 세션 조회 - 세션 시작(startSession) 시 동일 NFC 태그를 사용하는 다른 ACTIVE 세션이
     * 있는지 검증할 때 사용 (중복 활성 세션 방지)
     */
    List<AttendanceSession> findByNfcTagIdAndStatus(Long nfcTagId, SessionStatus status);
}