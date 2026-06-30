package com.attendance.domain.session.repository;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}